package marvin.com.br.diariobordomaquinario.repository;

import marvin.com.br.diariobordomaquinario.Model.SincronizacaoRequest;
import marvin.com.br.diariobordomaquinario.Model.VersaoResponse;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface RetroServiceInterface {

    @GET("/app_obras/versao_app")
    Call<VersaoResponse> verificarVersao();

    @POST("/app_obras/sincronizar")
    Call<ResponseBody> sincronizarTudo(@Body SincronizacaoRequest request);

}
