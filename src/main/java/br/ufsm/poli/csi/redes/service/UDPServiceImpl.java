package br.ufsm.poli.csi.redes.service;

import br.ufsm.poli.csi.redes.model.Mensagem;
import br.ufsm.poli.csi.redes.model.Usuario;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class UDPServiceImpl implements UDPService {

    private Usuario usuario;
    private Map<String, Usuario> usuariosConectados = new HashMap<>();
    private final CopyOnWriteArrayList<UDPServiceUsuarioListener> usuarioListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<UDPServiceMensagemListener> mensagemListeners = new CopyOnWriteArrayList<>();
    private Map<String, Long> ultimoContato = new HashMap<>();

    public UDPServiceImpl() {

    }

    private class EnviaSonda implements Runnable {
        private final ObjectMapper mapper = new ObjectMapper();

        @SneakyThrows
        @Override
        public void run() {
            while (true) {
                Thread.sleep(5000);
                if (usuario == null) continue;

                Mensagem mensagem = new Mensagem();
                mensagem.setTipoMensagem(Mensagem.TipoMensagem.sonda);
                mensagem.setUsuario(usuario.getNome());
                mensagem.setStatus(usuario.getStatus().toString());

                String strMensagem = mapper.writeValueAsString(mensagem);
                byte[] bMensagem = strMensagem.getBytes();

                for (int i = 1; i < 255; i++) {
                    try {
                        DatagramPacket pacote = new DatagramPacket(
                                bMensagem,
                                bMensagem.length,
                                InetAddress.getByName("172.16.155." + i), // ajuste a faixa de IP conforme necessário
                                8080
                        );
                        DatagramSocket socket = new DatagramSocket();
                        socket.send(pacote);
                        socket.close();
                    } catch (Exception ignored) {

                    }
                }
            }
        }
    }

    private void receberSondas() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(8080)) {
                byte[] buffer = new byte[1024];
                ObjectMapper mapper = new ObjectMapper();

                while (true) {
                    DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                    socket.receive(pacote);

                    String dados = new String(pacote.getData(), 0, pacote.getLength());
                    Mensagem msg = mapper.readValue(dados, Mensagem.class);


                    if (usuario != null && msg.getUsuario().equals(usuario.getNome())) {
                        continue;
                    }

                    switch (msg.getTipoMensagem()) {
                        case sonda -> {
                            Usuario u = usuariosConectados.get(msg.getUsuario());

                            if (u == null) {
                                Usuario.StatusUsuario status = (msg.getStatus() != null) ?
                                        Usuario.StatusUsuario.valueOf(msg.getStatus()) :
                                        Usuario.StatusUsuario.DISPONIVEL;

                                u = new Usuario(msg.getUsuario(), status, pacote.getAddress());
                                usuariosConectados.put(u.getNome(), u);

                                for (UDPServiceUsuarioListener l : usuarioListeners) {
                                    l.usuarioAdicionado(u);
                                }
                            } else if (msg.getStatus() != null) {
                                u.setStatus(Usuario.StatusUsuario.valueOf(msg.getStatus()));
                                for (UDPServiceUsuarioListener l : usuarioListeners) {
                                    l.usuarioAlterado(u);
                                }
                            }

                            // Atualiza o último contato do usuário
                            ultimoContato.put(msg.getUsuario(), System.currentTimeMillis());
                        }
                        case fim_chat -> {
                            Usuario remetente = usuariosConectados.get(msg.getUsuario());
                            if (remetente != null) {
                                recebendoFimChat(remetente);
                            }
                        }


                        case msg_individual, msg_grupo -> {
                            Usuario remetente = usuariosConectados.get(msg.getUsuario());

                            if (remetente == null) {
                                remetente = new Usuario(msg.getUsuario(),
                                        Usuario.StatusUsuario.DISPONIVEL,
                                        pacote.getAddress());
                                usuariosConectados.put(remetente.getNome(), remetente);
                                for (UDPServiceUsuarioListener l : usuarioListeners) {
                                    l.usuarioAdicionado(remetente);
                                }
                            }

                            boolean chatGeral = msg.getTipoMensagem() == Mensagem.TipoMensagem.msg_grupo;

                            for (UDPServiceMensagemListener l : mensagemListeners) {
                                l.mensagemRecebida(msg.getMsg(), remetente, chatGeral);
                            }
                        }

                        default -> {
                            // ignora outros tipos
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    @Override
    public void enviarMensagem(String mensagem, Usuario destinatario, boolean chatGeral) {
        if (usuario == null) return;

        try {
            ObjectMapper mapper = new ObjectMapper();
            Mensagem msg = new Mensagem();

            msg.setTipoMensagem(chatGeral ? Mensagem.TipoMensagem.msg_grupo : Mensagem.TipoMensagem.msg_individual);
            msg.setUsuario(usuario.getNome());
            msg.setStatus(usuario.getStatus().toString());
            msg.setMsg(mensagem);

            byte[] bMensagem = mapper.writeValueAsBytes(msg);
            DatagramPacket pacote;

            if (chatGeral) {
                for (int i = 1; i < 255; i++) {
                    try {
                        DatagramPacket pacoteEnvio = new DatagramPacket(
                                bMensagem,
                                bMensagem.length,
                                InetAddress.getByName("172.16.155." + i),
                                8080
                        );
                        DatagramSocket socket = new DatagramSocket();
                        socket.send(pacoteEnvio);
                        socket.close();
                    } catch (Exception ignored) {
                    }
                }
            } else {
                Usuario u = usuariosConectados.get(destinatario.getNome());
                if (u != null && u.getEndereco() != null) {
                    DatagramSocket socket = new DatagramSocket();
                    pacote = new DatagramPacket(bMensagem, bMensagem.length, u.getEndereco(), 8080);
                    socket.send(pacote);
                    socket.close();
                } else {
                    System.out.println("Não foi possível enviar: endereço do destinatário desconhecido");
                }
            }

            // notifica o listener local para ambos os casos (grupo e privado)
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
        this.usuario = usuario; // atualiza usuário
        // inicia envio de sondas em background
        new Thread(new EnviaSonda()).start();
        // inicia recebimento de sondas em background
        receberSondas();
        // inicia monitoramento de inativos
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
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10000); // verifica a cada 10s
                    long agora = System.currentTimeMillis();

                    // remove inativos
                    for (String nome : new HashMap<>(usuariosConectados).keySet()) {
                        long ultimo = ultimoContato.getOrDefault(nome, agora);
                        if (agora - ultimo > 30000) { // 30s inativo
                            Usuario u = usuariosConectados.remove(nome);
                            ultimoContato.remove(nome);

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


    private void recebendoFimChat(Usuario remetente) {
        System.out.println("Chat encerrado por: " + remetente.getNome());

        // Notifica apenas a interface do chat privado correspondente
        for (UDPServiceMensagemListener l : mensagemListeners) {
            l.mensagemRecebida("O chat foi encerrado pelo usuário " + remetente.getNome(), remetente, false);
        }

        // Não remove o usuário da lista de conectados
    }

    public void encerrarChat(Usuario destinatario) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Mensagem msg = new Mensagem();
            msg.setTipoMensagem(Mensagem.TipoMensagem.fim_chat);
            msg.setUsuario(usuario.getNome());
            msg.setStatus(usuario.getStatus().toString());

            byte[] bMensagem = mapper.writeValueAsBytes(msg);

            if (destinatario.getEndereco() != null) {
                DatagramSocket socket = new DatagramSocket();
                DatagramPacket pacote = new DatagramPacket(bMensagem, bMensagem.length,
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
