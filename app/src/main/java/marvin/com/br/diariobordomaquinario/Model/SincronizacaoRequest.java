package marvin.com.br.diariobordomaquinario.Model;

import java.util.List;

public class SincronizacaoRequest {
    private List<RegistroHorasMaquinasModel> cadastros;

    public SincronizacaoRequest(List<RegistroHorasMaquinasModel> cadastros) {
        this.cadastros = cadastros;
    }
    public List<RegistroHorasMaquinasModel> getCadastros() { return cadastros; }

}
