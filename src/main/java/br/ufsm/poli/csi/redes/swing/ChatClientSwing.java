package br.ufsm.poli.csi.redes.swing;

import br.ufsm.poli.csi.redes.model.Usuario;
import br.ufsm.poli.csi.redes.service.UDPService;
import br.ufsm.poli.csi.redes.service.UDPServiceImpl;
import br.ufsm.poli.csi.redes.service.UDPServiceMensagemListener;
import br.ufsm.poli.csi.redes.service.UDPServiceUsuarioListener;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class ChatClientSwing extends JFrame {

    private Usuario meuUsuario;
    private JList listaChat;
    private DefaultListModel<Usuario> dfListModel;
    private JTabbedPane tabbedPane = new JTabbedPane();
    private Map<String, PainelChatPVT> chatsAbertos = new HashMap<>();
    private UDPService udpService = new UDPServiceImpl();
    private Usuario USER_GERAL = new Usuario("Geral", null, null);

    public ChatClientSwing() throws UnknownHostException {
        setLayout(new GridBagLayout());
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Status");

        ButtonGroup group = new ButtonGroup();
        JRadioButtonMenuItem rbMenuItem = new JRadioButtonMenuItem(Usuario.StatusUsuario.DISPONIVEL.name());
        rbMenuItem.setSelected(true);
        rbMenuItem.addActionListener(e -> {
            ChatClientSwing.this.meuUsuario.setStatus(Usuario.StatusUsuario.DISPONIVEL);
            udpService.usuarioAlterado(meuUsuario);
        });
        group.add(rbMenuItem);
        menu.add(rbMenuItem);

        rbMenuItem = new JRadioButtonMenuItem(Usuario.StatusUsuario.NAO_PERTURBE.name());
        rbMenuItem.addActionListener(e -> {
            ChatClientSwing.this.meuUsuario.setStatus(Usuario.StatusUsuario.NAO_PERTURBE);
            udpService.usuarioAlterado(meuUsuario);
        });
        group.add(rbMenuItem);
        menu.add(rbMenuItem);

        rbMenuItem = new JRadioButtonMenuItem(Usuario.StatusUsuario.VOLTO_LOGO.name());
        rbMenuItem.addActionListener(e -> {
            ChatClientSwing.this.meuUsuario.setStatus(Usuario.StatusUsuario.VOLTO_LOGO);
            udpService.usuarioAlterado(meuUsuario);
        });
        group.add(rbMenuItem);
        menu.add(rbMenuItem);

        menuBar.add(menu);
        this.setJMenuBar(menuBar);

        tabbedPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    JPopupMenu popupMenu = new JPopupMenu();
                    final int tab = tabbedPane.getUI().tabForCoordinate(tabbedPane, e.getX(), e.getY());
                    if (tab >= 0) {
                        JMenuItem item = new JMenuItem("Fechar");
                        item.addActionListener(ev -> {
                            PainelChatPVT painel = (PainelChatPVT) tabbedPane.getComponentAt(tab);



                            // chamo a função para encerrar o chat
                            if (!painel.chatGeral) {
                                udpService.encerrarChat(painel.getUsuario());
                                painel.getUsuario();
                            }



                            tabbedPane.remove(tab);
                            chatsAbertos.remove(painel.getUsuario().getNome());
                        });
                        popupMenu.add(item);
                        popupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        add(new JScrollPane(criaLista()), new GridBagConstraints(0, 0, 1, 1, 0.1, 1,
                GridBagConstraints.WEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        add(tabbedPane, new GridBagConstraints(1, 0, 1, 1, 1, 1,
                GridBagConstraints.EAST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

        setSize(800, 600);
        final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        this.setLocation((screenSize.width - getWidth()) / 2, (screenSize.height - getHeight()) / 2);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("Chat P2P - Redes de Computadores");


        String nomeUsuario = JOptionPane.showInputDialog(this, "Digite seu nome de usuário:");
        if (nomeUsuario == null || nomeUsuario.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nome de usuário não pode ser vazio!");
            System.exit(0);
        }

        JPanel painelStatus = new JPanel(new GridLayout(2, 1));
        painelStatus.add(new JLabel("Selecione seu status:"));
        JComboBox<Usuario.StatusUsuario> comboStatus = new JComboBox<>(Usuario.StatusUsuario.values());
        painelStatus.add(comboStatus);

        int result = JOptionPane.showConfirmDialog(
                this,
                painelStatus,
                "Status do Usuário",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            JOptionPane.showMessageDialog(this, "É necessário selecionar um status para continuar!");
            System.exit(0);
        }

        Usuario.StatusUsuario statusSelecionado = (Usuario.StatusUsuario) comboStatus.getSelectedItem();
        this.meuUsuario = new Usuario(nomeUsuario, statusSelecionado, InetAddress.getLocalHost());


        setTitle("Chat P2P - " + nomeUsuario + " (" + statusSelecionado.name() + ")");

        // Inicia serviços
        udpService.usuarioAlterado(meuUsuario);
        udpService.addListenerMensagem(new MensagemListener());
        udpService.addListenerUsuario(new UsuarioListener());

        setVisible(true);
    }

    private JComponent criaLista() {
        dfListModel = new DefaultListModel<>();
        listaChat = new JList(dfListModel);
        listaChat.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    int index = listaChat.locationToIndex(evt.getPoint());
                    Usuario user = (Usuario) listaChat.getModel().getElementAt(index);
                    if (!user.equals(meuUsuario) && !chatsAbertos.containsKey(user.getNome())) {
                        PainelChatPVT painel = new PainelChatPVT(user, false);
                        tabbedPane.add(user.toString(), painel);
                        chatsAbertos.put(user.getNome(), painel);
                    }
                }
            }
        });

        PainelChatPVT geral = new PainelChatPVT(USER_GERAL, true);
        tabbedPane.add("Geral", geral);
        chatsAbertos.put(USER_GERAL.getNome(), geral);

        return listaChat;
    }

    @Getter
    class PainelChatPVT extends JPanel {
        JTextArea areaChat;
        JTextField campoEntrada;
        Usuario usuario;
        boolean chatGeral = false;

        PainelChatPVT(Usuario usuario, boolean chatGeral) {
            setLayout(new GridBagLayout());
            areaChat = new JTextArea();
            areaChat.setEditable(false);
            campoEntrada = new JTextField();
            this.usuario = usuario;
            this.chatGeral = chatGeral;

            campoEntrada.addActionListener(e -> {
                String texto = e.getActionCommand();
                campoEntrada.setText("");
                areaChat.append(meuUsuario.getNome() + "> " + texto + "\n");
                udpService.enviarMensagem(texto, usuario, chatGeral);
            });

            add(new JScrollPane(areaChat), new GridBagConstraints(0, 0, 1, 1, 1, 1,
                    GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
            add(campoEntrada, new GridBagConstraints(0, 1, 1, 1, 1, 0,
                    GridBagConstraints.SOUTH, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        }
    }

    private class UsuarioListener implements UDPServiceUsuarioListener {
        @Override
        public void usuarioAdicionado(Usuario usuario) {
            dfListModel.removeElement(usuario);
            dfListModel.addElement(usuario);
        }

        @Override
        public void usuarioRemovido(Usuario usuario) {
            dfListModel.removeElement(usuario);
        }

        @Override
        public void usuarioAlterado(Usuario usuario) {
            dfListModel.removeElement(usuario);
            dfListModel.addElement(usuario);
        }
    }

    private class MensagemListener implements UDPServiceMensagemListener {
        @Override
        public void mensagemRecebida(String mensagem, Usuario remetente, boolean chatGeral) {
            if (!chatGeral && remetente.getNome().equals(meuUsuario.getNome())) {
                return;
            }

            PainelChatPVT painel;
            if (chatGeral) {
                painel = chatsAbertos.get(USER_GERAL.getNome());
            } else {
                painel = chatsAbertos.get(remetente.getNome());
                if (painel == null) {
                    painel = new PainelChatPVT(remetente, false);
                    tabbedPane.add(remetente.toString(), painel);
                    chatsAbertos.put(remetente.getNome(), painel);
                }
            }

            if (painel != null) {
                painel.getAreaChat().append(remetente.getNome() + "> " + mensagem + "\n");
            }
        }
    }
}
