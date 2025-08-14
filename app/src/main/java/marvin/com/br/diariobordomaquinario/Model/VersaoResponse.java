package marvin.com.br.diariobordomaquinario.Model;

import com.google.gson.annotations.SerializedName;

public class VersaoResponse {
    @SerializedName("versao_atual")
    private String versaoAtual;

    @SerializedName("obrigatorio")
    private boolean obrigatorio;

    @SerializedName("link")
    private String link;

    // Getters
    public String getVersaoAtual() { return versaoAtual; }
    public boolean isObrigatorio() { return obrigatorio; }
    public String getLink() { return link; }
}
