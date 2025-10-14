package br.ufsm.poli.csi.redes.model;

public class Mensagem {

    private TipoMensagem tipoMensagem;
    private String usuario;
    private String status;
    private String msg;

    public enum TipoMensagem {
        sonda, fim_chat, msg_individual, msg_grupo
    }

    public TipoMensagem getTipoMensagem() {
        return tipoMensagem;
    }

    public void setTipoMensagem(TipoMensagem tipoMensagem) {
        this.tipoMensagem = tipoMensagem;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
