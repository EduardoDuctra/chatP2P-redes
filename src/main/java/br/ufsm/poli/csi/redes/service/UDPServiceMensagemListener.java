package br.ufsm.poli.csi.redes.service;

import br.ufsm.poli.csi.redes.model.Usuario;

public interface UDPServiceMensagemListener {

    /**
     * Notifica que uma mensagem foi recebida
     * @param mensagem
     * @param remetente
     * @param chatGeral
     */
    void mensagemRecebida(String mensagem, Usuario remetente, boolean chatGeral);




}
