package marvin.com.br.diariobordomaquinario.DAO;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static Retrofit retrofit;

    public static Retrofit getClient(Context context) {

        Gson gson = new GsonBuilder().setLenient().create();

        if (retrofit == null) {

            retrofit = new Retrofit.Builder()

                    .baseUrl("http://24.152.35.148:8008")
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}