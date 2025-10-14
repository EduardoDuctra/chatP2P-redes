package br.ufsm.poli.csi.redes.service;

import br.ufsm.poli.csi.redes.model.Usuario;

public interface UDPServiceUsuarioListener {

    /**
     * Notifica que um usuário foi adicionado
     * @param usuario
     */
    void usuarioAdicionado(Usuario usuario);

    /**
     * Notifica que um usuário foi removido
     * @param usuario
     */
    void usuarioRemovido(Usuario usuario);

    /**
     * Notifica que um usuário foi alterado
     * @param usuario
     */
    void usuarioAlterado(Usuario usuario);

}
