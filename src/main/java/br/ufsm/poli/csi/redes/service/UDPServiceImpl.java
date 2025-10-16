package br.ufsm.poli.csi.redes.service;

import br.ufsm.poli.csi.redes.model.Mensagem;
import br.ufsm.poli.csi.redes.model.Usuario;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class UDPServiceImpl implements UDPService {

    private Usuario usuario;
    private Map<String, Usuario> usuariosConectados = new HashMap<>();

    //Uma lista compartilhada em threads. Porque? todas as threads usam a mesma lista. Se uma atualizar, todas atualizam
    private final CopyOnWriteArrayList<UDPServiceUsuarioListener> usuarioListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<UDPServiceMensagemListener> mensagemListeners = new CopyOnWriteArrayList<>();

    //Armazena o usuario e o ultimo momento ativo. Porque? remove depois de 30s fechado
    private Map<String, Long> ultimoContato = new HashMap<>();

    private String ipRede = "192.168.100.";

    public UDPServiceImpl() {

    }


    //recebe um array de bytes de mensagem e passa para um usuario de destino
    private void enviarPacote(byte[] mensagemByte, String ipDestino) throws Exception {
      //cria um pacote UDP com os dados da mensagem, comprimento, destino e porta
        DatagramPacket pacote = new DatagramPacket(
                mensagemByte,
                mensagemByte.length,
                InetAddress.getByName(ipDestino),
                8080
        );

        //socket => caminho entre dois computadores. Usa o ip + porta
        //socket temporario para enviar a mensagem
        DatagramSocket socket = new DatagramSocket();
        //caminho para envio da conexão
        socket.send(pacote);
        socket.close();
    }



    //cria um thread, que a cada 5s envia uma sonda.
    // Porque? saber se os usuários estão conectados
    private class EnviaSonda implements Runnable {

        //classe para conveter json em objetos
        //Porque? converte a mensagem que recebo no UDP em objeto Mensagem
        private final ObjectMapper mapper = new ObjectMapper();

        @SneakyThrows
        @Override
        public void run() {


            while (true) {

                Thread.sleep(5000);

                if (usuario == null){
                    continue;
                }

                //estou enviando uma mensagem do tipo sonda a cada 5s
                Mensagem mensagem = new Mensagem();
                mensagem.setTipoMensagem(Mensagem.TipoMensagem.sonda);
                mensagem.setUsuario(usuario.getNome());
                mensagem.setStatus(usuario.getStatus().toString());

                //conversões Json e Mensagem
                String mensagemConvertida = mapper.writeValueAsString(mensagem);
                byte[] bMensagem = mensagemConvertida.getBytes();

                //como vou pegar todos os ips da rede? com esse loop
                for (int i = 1; i < 255; i++) {
                    try {
                        enviarPacote(bMensagem, ipRede + i);
                    } catch (IOException e) {

                    }
                }
            }
        }
    }

    private void receberSondas() {

        //crio uma thread para receber e uma para enviar. Essa é a de receber
        new Thread(() -> {

            //datagramsockect é sempre que quero receber pacote
            //criação de um socket para recebimento na 8080. Vai pegar os dados e colocar no byte []
            try (DatagramSocket socket = new DatagramSocket(8080)) {
                byte[] buffer = new byte[1024];
                ObjectMapper mapper = new ObjectMapper();

                while (true) {
                    //cria um UDP para armazenar o dados que chegam no buffer
                    DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                    //bloqueia a thread até que o pacote chegue. Função do DatagramSocket.
                    // DatagramSocket guarda o ip e porta
                    //recebi o pacote e armazeno no UDP que criei ali em cima
                    socket.receive(pacote);

                    //dados recebidos pelo UDP e converte em string
                    //porque? pq vieram no formato json e  preciso deles no formato Mensagem
                    String dados = new String(pacote.getData(), 0, pacote.getLength());
                    Mensagem msg = mapper.readValue(dados, Mensagem.class);


                    if (usuario != null && msg.getUsuario().equals(usuario.getNome())) {
                        continue;
                    }

                    switch (msg.getTipoMensagem()) {
                        case sonda -> {

                            Usuario u = usuariosConectados.get(msg.getUsuario());

                            //se o usuario nao tiver na lista. Cria um novo
                            //se ele vier com valor null no status, atribuo como disponivel
                            if (u == null) {
                                Usuario.StatusUsuario status = (msg.getStatus() != null) ?
                                        Usuario.StatusUsuario.valueOf(msg.getStatus()) :
                                        Usuario.StatusUsuario.DISPONIVEL;

                                u = new Usuario(msg.getUsuario(), status, pacote.getAddress());
                                usuariosConectados.put(u.getNome(), u);

                                //coloco na lista de listners do usuário
                                for (UDPServiceUsuarioListener l : usuarioListeners) {
                                    l.usuarioAdicionado(u);
                                }
                            } else if (msg.getStatus() != null) {
                                //se ele alterar o status, atualizo na lista de listners
                                u.setStatus(Usuario.StatusUsuario.valueOf(msg.getStatus()));
                                for (UDPServiceUsuarioListener l : usuarioListeners) {
                                    l.usuarioAlterado(u);
                                }
                            }

                          //armazeno a ultima vez que ele esteve conectado?
                            //Porque? pq se ficar mais de 30s fora da lista, removo
                            ultimoContato.put(msg.getUsuario(), System.currentTimeMillis());
                        }
                        case fim_chat -> {
                            Usuario remetente = usuariosConectados.get(msg.getUsuario());
                            if (remetente != null) {
                               //se recebo o fim_chat e chamo recebendoFimChat
                                recebendoFimChat(remetente);
                            }
                        }


                        case msg_individual, msg_grupo -> {


                            Usuario remetente = usuariosConectados.get(msg.getUsuario());

                            //se o usuario nao tiver na lista. Cria um novo
                            //se ele vier com valor null no status, atribuo como disponivel
                            if (remetente == null) {
                                remetente = new Usuario(msg.getUsuario(),
                                        Usuario.StatusUsuario.DISPONIVEL,
                                        //recebo o pacote e pego o seu ip+porta
                                        pacote.getAddress());
                                //coloco esse usuario na lista de usuarios conectados
                                usuariosConectados.put(remetente.getNome(), remetente);
                                //notifico os litners
                                for (UDPServiceUsuarioListener l : usuarioListeners) {
                                    l.usuarioAdicionado(remetente);
                                }
                            }

//                            se for mensagem do chat_geral, notifico todos os listners que eles tem mensagem
                            boolean chatGeral = msg.getTipoMensagem() == Mensagem.TipoMensagem.msg_grupo;

                            for (UDPServiceMensagemListener l : mensagemListeners) {
                                l.mensagemRecebida(msg.getMsg(), remetente, chatGeral);
                            }
                        }

//                        default -> {
//                            // ignora outros tipos
//                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }



    @Override
    public void enviarMensagem(String mensagem, Usuario destinatario, boolean chatGeral) {
        if (usuario == null) {
            return;
        }

        try {
            //conversor de objetos
            ObjectMapper mapper = new ObjectMapper();
            Mensagem mensagemEnviar = new Mensagem();

            mensagemEnviar.setTipoMensagem(chatGeral ? Mensagem.TipoMensagem.msg_grupo : Mensagem.TipoMensagem.msg_individual);
            mensagemEnviar.setUsuario(usuario.getNome());
            mensagemEnviar.setStatus(usuario.getStatus().toString());
            mensagemEnviar.setMsg(mensagem);

            //converte a mensagem em um array de bytes
            byte[] mensagemConvertida = mapper.writeValueAsBytes(mensagemEnviar);
            //crio um pacote UDP para enviar
            DatagramPacket pacote;

            //se for msg_geral mando para todos os usuariso da rede
            if (chatGeral) {
                for (int i = 1; i < 255; i++) {
                    try {
                        enviarPacote(mensagemConvertida, ipRede + i);
                    } catch (IOException e) {

                    }
                }
            } else {
                Usuario u = usuariosConectados.get(destinatario.getNome());
                if (u != null && u.getEndereco() != null) {

                    //crio um pacote UDP para enviar e envio a mensagem em bytes.
                    // No socket armazeno o endereço (ip + porta)
                    DatagramSocket socket = new DatagramSocket();
                    pacote = new DatagramPacket(mensagemConvertida, mensagemConvertida.length, u.getEndereco(), 8080);

                    socket.send(pacote);
                    socket.close();
                } else {
                    System.out.println("Não foi possível enviar: endereço do destinatário desconhecido");
                }
            }

            // notifica todos os listener
            // Cada listener decide se a mensagem é relevante para o usuário que ele representa
            if (!chatGeral) {
                for (UDPServiceMensagemListener l : mensagemListeners) {
                    l.mensagemRecebida(mensagem, usuario, chatGeral);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void usuarioAlterado(Usuario usuario) {
        this.usuario = usuario;

        //cria UMA thread de envio de sonda para monitoras todos usuários
        //ela vaai monitorar cada usuario a cada 5s
        new Thread(new EnviaSonda()).start();

        //recebe as sondas
        receberSondas();


        monitorarUsuariosInativos();
    }

    @Override
    public void addListenerMensagem(UDPServiceMensagemListener listener) {
        mensagemListeners.add(listener);
    }

    @Override
    public void addListenerUsuario(UDPServiceUsuarioListener listener) {
        usuarioListeners.add(listener);
    }

    private void monitorarUsuariosInativos() {

        //crio uma thread para monitorar usuarios
        //a função dela é monitorar apenas. Tenho a de envio, receber e monitorar
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10000);
                    long agora = System.currentTimeMillis();

                    // Percorre todos os usuários conectados. Copia a lista
                    for (String nome : new HashMap<>(usuariosConectados).keySet()) {
                        //obtem o ultimo contato. se nao tiver, pega o horario atual
                        long ultimo = ultimoContato.getOrDefault(nome, agora);
                        //se o usuario estiver inativo a mais de 30s, remove
                        //30s sem receber sonda alguma
                        if (agora - ultimo > 30000) {
                            Usuario u = usuariosConectados.remove(nome);
                            ultimoContato.remove(nome);

                            //notifica os listners que ele foi removido
                            if (u != null) {
                                for (UDPServiceUsuarioListener l : usuarioListeners) {
                                    l.usuarioRemovido(u);
                                }
                                System.out.println("Usuário removido por inatividade: " + u.getNome());
                            }
                        }
                    }

                    // imprime todos os usuários conectados
                    System.out.println("Usuarios conectados: " + usuariosConectados.keySet());

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }


    //imprime quem mandou e notifica o listners do destinatario
    private void recebendoFimChat(Usuario remetente) {
        System.out.println("Chat encerrado por: " + remetente.getNome());

        // percorre todos os listeners e envia a mensagem de fim de chat
        //vou tratar e ver se eu tenho um chat aberto com esse usuario para mostrar a mensagem
        for (UDPServiceMensagemListener l : mensagemListeners) {
            l.mensagemRecebida("O chat foi encerrado pelo usuário " + remetente.getNome(), remetente, false);
        }

    }

    //função que chamo na interface, passando o usuario com quem vai receber a sonda fim_chat
    public void encerrarChat(Usuario destinatario) {
        try {

            ObjectMapper mapper = new ObjectMapper();
            Mensagem msg = new Mensagem();
            msg.setTipoMensagem(Mensagem.TipoMensagem.fim_chat);
            msg.setUsuario(usuario.getNome());
            msg.setStatus(usuario.getStatus().toString());

            byte[] mensagem = mapper.writeValueAsBytes(msg);

            if (destinatario.getEndereco() != null) {

                //crio um DatagramSocket para enviar o pacote UDP
                DatagramSocket socket = new DatagramSocket();
                //envio a mensagem, o ip do destinatario e porta
                DatagramPacket pacote = new DatagramPacket(mensagem, mensagem.length,
                        destinatario.getEndereco(), 8080);
                socket.send(pacote);
                socket.close();

                System.out.println("Mensagem de fim de chat enviada para: " + destinatario.getNome());
            } else {
                System.out.println("Não foi possível enviar: endereço do destinatário desconhecido");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }




}
