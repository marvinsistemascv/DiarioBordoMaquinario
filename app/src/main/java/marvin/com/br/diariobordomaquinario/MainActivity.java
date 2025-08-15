package marvin.com.br.diariobordomaquinario;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;                 // <— use sempre AppCompat
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import marvin.com.br.diariobordomaquinario.DAO.ApiClient;
import marvin.com.br.diariobordomaquinario.DAO.AppDatabase;
import marvin.com.br.diariobordomaquinario.Model.EncarregadoModel;
import marvin.com.br.diariobordomaquinario.Model.RegistroHorasMaquinasModel;
import marvin.com.br.diariobordomaquinario.Model.SincronizacaoRequest;
import marvin.com.br.diariobordomaquinario.Model.VersaoResponse;
import marvin.com.br.diariobordomaquinario.repository.RetroServiceInterface;
import marvin.com.br.diariobordomaquinario.util.DataHora;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class MainActivity extends AppCompatActivity {

    private TextView txtVersao;
    private TextView txtEncarregado;
    private TextView txtLocalObra;
    private AppDatabase db;
    private AlertDialog dialog;                         // <— diálogo atual
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean pediuCadastroEncarregado = false;   // evita abrir 2x
    private RetroServiceInterface service;
    Retrofit retrofit;

    private final ActivityResultLauncher<Intent> qrLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String qr = result.getData().getStringExtra("qr_text");
                    Integer veiculoId = extrairIdVeiculo(qr);
                    if (veiculoId != null) {
                        MediaPlayer mp = MediaPlayer.create(this, R.raw.camera_som);
                        mp.setOnCompletionListener(MediaPlayer::release);
                        mp.start();
                        iniciar_viagem(veiculoId);
                    } else {
                        Toast.makeText(this, "QR inválido", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        retrofit = ApiClient.getClient(this);
        service = retrofit.create(RetroServiceInterface.class);

        verificarAtualizacao();

        txtVersao = findViewById(R.id.txtVersao);
        txtEncarregado = findViewById(R.id.txt_nome_encarregado);
        txtLocalObra = findViewById(R.id.txt_local_obra);

        ImageView btn_brasao = findViewById(R.id.btn_brasao);

        btn_brasao.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            MediaPlayer mp = MediaPlayer.create(this, R.raw.button_click);
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.start();
            Intent it = new Intent(this, QrScannerActivity.class);
            qrLauncher.launch(it);
        });

        txtVersao.setText("v: " + BuildConfig.VERSION_NAME);

        try {
            db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "app-db")
                    .fallbackToDestructiveMigration()
                    .build();
        } catch (Exception e) {
            showToastErro("ERRO DB: " + e.getMessage());
        }

        // Carrega encarregado e decide se dá boas-vindas ou pede cadastro.
        io.execute(() -> {
            EncarregadoModel e = null;
            try {
                e = db.encarregadoDAO().pegar_encarregado();
            } catch (Exception ex) {
                EncarregadoModel finalE = e;
                runOnUiThread(() -> showToast("Erro ao ler encarregado: " + ex.getMessage()));
            }

            EncarregadoModel finalE = e;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;

                if (finalE != null) {
                    txtEncarregado.setText(finalE.nome);
                    txtLocalObra.setText(finalE.local_obra);
                    // segue o fluxo normal da Main sem finalizar aqui
                } else if (!pediuCadastroEncarregado) {
                    pediuCadastroEncarregado = true;
                    abrir_cadastro_encarregado();
                }
            });
        });
    }

    private void abrir_cadastro_encarregado() {
        if (isFinishing() || isDestroyed()) return;

        View viewInflated = LayoutInflater.from(this)
                .inflate(R.layout.dialog_cad_encarregado, null);

        EditText inputNome = viewInflated.findViewById(R.id.inputNomeEncarregado);
        EditText inputLocal = viewInflated.findViewById(R.id.inputLocalEncarregado);
        Button btnAdicionar = viewInflated.findViewById(R.id.btnGravaEncarregado);
        Button btnCancelar = viewInflated.findViewById(R.id.btnCancelarEncarregado);

        EncarregadoModel encarregado = pegar_encarregado();
        if (encarregado != null) {
            inputNome.setText(encarregado.nome);
            inputLocal.setText(encarregado.local_obra);
        }

        dialog = new AlertDialog.Builder(this)
                .setView(viewInflated)
                .create();

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        // impede fechar tocando fora
        dialog.setCanceledOnTouchOutside(false);
        // impede fechar pelo botão "voltar"
        dialog.setCancelable(false);

        btnCancelar.setOnClickListener(v -> {
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        });

        btnAdicionar.setOnClickListener(v -> {
            String nome = inputNome.getText().toString().trim();
            String local = inputLocal.getText().toString().trim();
            if (nome.isEmpty()) {
                inputNome.setError("Informe o nome");
                return;
            }
            if (local.isEmpty()) {
                inputLocal.setError("informe o local da obra");
                return;
            }

            btnAdicionar.setEnabled(false);
            btnCancelar.setEnabled(false);

            io.execute(() -> {
                try {
                    EncarregadoModel e = new EncarregadoModel();
                    e.nome = nome;
                    e.local_obra = local;
                    e.id = 1;
                    db.encarregadoDAO().inserir(e);

                    runOnUiThread(() -> {
                        if (dialog != null && dialog.isShowing()) dialog.dismiss();
                        txtEncarregado.setText(e.nome);
                        txtLocalObra.setText(e.local_obra);
                        // se sua intenção é sair da Main após cadastrar, finalize AQUI (seguro):
                        // if (!isFinishing() && !isDestroyed()) finish();
                    });
                } catch (Exception ex) {
                    runOnUiThread(() -> {
                        btnAdicionar.setEnabled(true);
                        btnCancelar.setEnabled(true);
                        showToastErro(ex.getMessage());
                    });
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        dialog = null;
        io.shutdownNow();
        super.onDestroy();
    }

    @Nullable
    public static Integer extrairIdVeiculo(String url) {
        if (url == null) return null;
        Pattern p = Pattern.compile("/home/(\\d+)(?:/|\\?|#|$)");
        Matcher m = p.matcher(url.trim());
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_option_1) {
            abrir_cadastro_encarregado();
            return true;
        }

        if (id == R.id.menu_option_2) {
            exibirDialogComListaEmCurso();
            return true;
        }

        if (id == R.id.menu_option_3) {
            new AlertDialog.Builder(this)
                    .setTitle("Sincronizar")
                    .setMessage("Deseja sincronizar os dados?")
                    .setPositiveButton("Sim", (d, which) -> {
                        sincronizar_cadastros();
                    })
                    .setNegativeButton("Cancelar", (d, which) -> d.dismiss())
                    .show();
            return true;
        }
        if (id == R.id.menu_option_4) {
            new AlertDialog.Builder(this)
                    .setTitle(" * Atenção *")
                    .setIcon(R.drawable.ic_atencao)
                    .setMessage("esta função apaga os dados sincronizados do seu dispositivo," +
                            " tem certeza que deseja prosseguir?")
                    .setPositiveButton("Sim, Apagar", (d, which) -> {
                        zerar_dados_sincronizados();
                    })
                    .setNegativeButton("Cancelar", (d, which) -> d.dismiss())
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void verificarAtualizacao() {
        Call<VersaoResponse> call = service.verificarVersao();

        call.enqueue(new Callback<VersaoResponse>() {
            @Override
            public void onResponse(Call<VersaoResponse> call, Response<VersaoResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    VersaoResponse versao = response.body();

                    String versaoApp = BuildConfig.VERSION_NAME;

                    if (!versaoApp.equals(versao.getVersaoAtual())) {
                        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(MainActivity.this)
                                .setTitle("Atualização disponível")
                                .setMessage("Há uma nova versão do aplicativo disponível.")
                                .setIcon(R.drawable.ic_sinc)
                                .setPositiveButton("Atualizar agora", (dialog, which) -> {
                                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(versao.getLink()));
                                    startActivity(intent);
                                })
                                .setCancelable(!versao.isObrigatorio());

                        if (!versao.isObrigatorio()) {
                            builder.setNegativeButton("Agora não", null);
                        }

                        builder.show();
                    }
                }
            }

            @Override
            public void onFailure(Call<VersaoResponse> call, Throwable t) {
                Log.e("VERSAO", "Erro ao verificar versão: " + t.getMessage());
            }
        });
    }

    public void iniciar_viagem(Integer cod_veiculo) {
        io.execute(() -> {
            RegistroHorasMaquinasModel ultima = null;
            try {
                ultima = db.registroHorasMaquinasDao().pegarUltimaCorridaMaquina(cod_veiculo);
                //caso seja primeiro registro da maquina
                if (ultima == null) {
                    ultima = new RegistroHorasMaquinasModel();

                    ultima.qtd_horas_inicio = 0.0;
                    ultima.qtd_horas_final = 0.0;
                    ultima.sit = "nulo";
                    ultima.cod_maquina = cod_veiculo;

                }
            } catch (Exception ex) {
                RegistroHorasMaquinasModel finalUltima = ultima;
                runOnUiThread(() -> showToast("Erro ao buscar última corrida: " + ex.getMessage()));
            }

            RegistroHorasMaquinasModel finalUltima = ultima;

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!finalUltima.sit.equals("em curso")) {
                    abrirDialogNovaViagem(finalUltima, cod_veiculo);
                } else if (finalUltima.sit.equals("em curso")) {
                    abrirDialogFinalizarViagem(finalUltima);
                }
            });
        });
    }

    private void abrirDialogNovaViagem(RegistroHorasMaquinasModel ultima, Integer cod_veiculo) {

        if (isFinishing() || isDestroyed()) return;

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_nova_viagem, null);

        EditText inputOperador = viewInflated.findViewById(R.id.inputOperador);
        EditText inputQtdHoras = viewInflated.findViewById(R.id.inputQtdHorasAtual);

        if (ultima.qtd_horas_final != null) {
            inputQtdHoras.setText(ultima.qtd_horas_final.toString());
        } else {
            inputQtdHoras.setText("0.0");
        }
        Button btnAdicionar = viewInflated.findViewById(R.id.btnConfirmarNovaCorrida);
        //Button btnCancelar = viewInflated.findViewById(R.id.btnCancelar);


        dialog = new AlertDialog.Builder(this)
                .setView(viewInflated)
                .create();

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnAdicionar.setOnClickListener(v -> {
            String operador = inputOperador.getText().toString().trim();
            String horimentro = inputQtdHoras.getText().toString().trim();

            if (operador.isEmpty()) {
                inputOperador.setError("Informe a operador");
                return;
            }
            if (horimentro.isEmpty()) {
                inputQtdHoras.setError("Informe o horímetro");
                return;
            }

            btnAdicionar.setEnabled(false);
            // btnCancelar.setEnabled(false);

            io.execute(() -> {
                try {

                    RegistroHorasMaquinasModel r = new RegistroHorasMaquinasModel();
                    r.cod_maquina = cod_veiculo;
                    r.data_inicio = DataHora.data_atual();
                    r.hora_inicio = DataHora.pegar_hora();
                    r.encarregado = txtEncarregado.getText().toString();
                    r.operador = operador;
                    r.sit = "em curso";
                    try {
                        String valorStr = horimentro.trim().replace(",", ".");
                        r.qtd_horas_inicio = valorStr.isEmpty() ? 0.0 : Double.parseDouble(valorStr);
                    } catch (NumberFormatException e) {
                        r.qtd_horas_inicio = 0.0; // ou outro valor padrão
                    }
                    db.registroHorasMaquinasDao().inserir(r);

                    runOnUiThread(() -> {
                        dialog.dismiss();
                        // if (dialog.isShowing()) dialog.dismiss();
                        showToast("Registro gravado");
                        // if (!isFinishing() && !isDestroyed()) finish();
                    });
                } catch (Exception ex) {
                    runOnUiThread(() -> {
                        btnAdicionar.setEnabled(true);
                        //btnCancelar.setEnabled(true);
                        showToastErro(ex.getMessage());
                    });
                }
            });
        });
    }

    private void abrirDialogFinalizarViagem(RegistroHorasMaquinasModel registro_curso) {

        new android.app.AlertDialog.Builder(this)
                .setTitle("Finalizar Registro")
                .setMessage("confirma o encerramento?")
                .setIcon(R.drawable.ic_question) // use um ícone de alerta ou qualquer outro
                .setPositiveButton("Sim", (dialog, which) -> {
                    finalizar_registro(registro_curso); // chama o método que tenta pegar a localização
                })
                .setNegativeButton("Não", (dialog, which) -> {

                })
                .show();
    }

    private void finalizar_registro(RegistroHorasMaquinasModel r) {

        View viewInflated = LayoutInflater.from(this)
                .inflate(R.layout.dialog_finalizar_registro, null);

        EditText inputHorimetro = viewInflated.findViewById(R.id.inputHorimetro);
        Button btnAdicionar = viewInflated.findViewById(R.id.btnEncerrarRegistro);

        dialog = new AlertDialog.Builder(this)
                .setView(viewInflated)
                .create();

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnAdicionar.setOnClickListener(v -> {

            String horimetroTexto = inputHorimetro.getText().toString().trim();

            // Conversão segura para Double
            Double horimetro_final;
            try {
                String valorStr = horimetroTexto.replace(",", ".").trim();
                if (valorStr.isEmpty()) {
                    inputHorimetro.setError("Informe o horímetro final");
                    return;
                }
                horimetro_final = Double.parseDouble(valorStr);
            } catch (NumberFormatException e) {
                inputHorimetro.setError("Valor inválido. Use ponto ou vírgula para decimais.");
                return;
            }

            // Captura antecipada para evitar null dentro da thread
            final Double inicio = r.qtd_horas_inicio != null ? r.qtd_horas_inicio : 0.0;

            // Verifica se o final é menor que o inicial
            if (horimetro_final < inicio) {
                android.app.AlertDialog.Builder msg = new android.app.AlertDialog.Builder(this);
                msg.setTitle("ERRO HORÍMETRO");
                msg.setIcon(R.drawable.ic_erro);
                msg.setMessage("Valor final não pode ser menor que o inicial.\n\nInicial: "
                        + inicio + "\nFinal: " + horimetro_final);
                msg.setPositiveButton("Entendi", null);
                msg.create().show();
                return;
            }

            btnAdicionar.setEnabled(false);

            io.execute(() -> {
                try {
                    Double qtd_horas = horimetro_final - inicio;
                    EncarregadoModel encarregado = pegar_encarregado();
                    r.sit = "finalizado";
                    r.data_termino = DataHora.data_atual();
                    r.hora_termino = DataHora.pegar_hora();
                    r.qtd_horas_final = horimetro_final;
                    r.encarregado = encarregado.nome;
                    r.local_obra = encarregado.local_obra;

                    if (r.total_horas_trabalhadas == null) {
                        r.total_horas_trabalhadas = 0.0;
                    }
                    r.total_horas_trabalhadas += qtd_horas;

                    db.registroHorasMaquinasDao().inserir(r);

                    runOnUiThread(() -> {
                        if (dialog != null && dialog.isShowing()) dialog.dismiss();
                        showToast("Registro finalizado");
                    });

                } catch (Exception ex) {
                    runOnUiThread(() -> {
                        btnAdicionar.setEnabled(true);
                        showToastErro(ex.getLocalizedMessage());
                    });
                }
            });
        });
    }

    private void showToast(String msge) {
        android.app.AlertDialog.Builder msg = new android.app.AlertDialog.Builder(this);
        msg.setTitle("Sucesso");
        msg.setIcon(R.drawable.ic_success);
        msg.setMessage(msge);
        msg.setPositiveButton("ok", null);
        msg.create().show();
    }

    private void showToastErro(String msge) {
        android.app.AlertDialog.Builder msg = new android.app.AlertDialog.Builder(this);
        msg.setTitle("ERRO");
        msg.setIcon(R.drawable.ic_erro);
        msg.setMessage(msge);
        msg.setPositiveButton("entendi", null);
        msg.create().show();
    }

    private void exibirDialogComListaEmCurso() {
        new Thread(() -> {
            List<RegistroHorasMaquinasModel> lista = db.registroHorasMaquinasDao().pegar_registros_em_curso();

            runOnUiThread(() -> {
                LayoutInflater inflater = LayoutInflater.from(this);
                View view = inflater.inflate(R.layout.dialog_lista_registros, null);
                LinearLayout layout = view.findViewById(R.id.layout_lista_registros);

                // Cabeçalho
                LinearLayout linhaCabecalho = new LinearLayout(this);
                linhaCabecalho.setOrientation(LinearLayout.HORIZONTAL);

                TextView cabecalho1 = new TextView(this);
                cabecalho1.setText("Máquina");
                cabecalho1.setPadding(8, 8, 8, 8);
                cabecalho1.setTypeface(Typeface.DEFAULT_BOLD);
                cabecalho1.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                TextView cabecalho2 = new TextView(this);
                cabecalho2.setText("Operador");
                cabecalho2.setPadding(8, 8, 8, 8);
                cabecalho2.setTypeface(Typeface.DEFAULT_BOLD);
                cabecalho2.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                TextView cabecalho3 = new TextView(this);
                cabecalho3.setText("Inicial");
                cabecalho3.setPadding(8, 8, 8, 8);
                cabecalho3.setTypeface(Typeface.DEFAULT_BOLD);
                cabecalho3.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                linhaCabecalho.addView(cabecalho1);
                linhaCabecalho.addView(cabecalho2);
                linhaCabecalho.addView(cabecalho3);
                layout.addView(linhaCabecalho);

                // Linhas de dados
                for (RegistroHorasMaquinasModel prop : lista) {
                    LinearLayout linha = new LinearLayout(this);
                    linha.setOrientation(LinearLayout.HORIZONTAL);

                    TextView col1 = new TextView(this);
                    col1.setText(String.valueOf(prop.cod_maquina));
                    col1.setPadding(8, 8, 8, 8);
                    col1.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                    TextView col2 = new TextView(this);
                    col2.setText(prop.operador != null ? prop.operador : "");
                    col2.setPadding(8, 8, 8, 8);
                    col2.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                    TextView col3 = new TextView(this);
                    col3.setText(prop.qtd_horas_inicio != null ? String.valueOf(prop.qtd_horas_inicio) : "");
                    col3.setPadding(8, 8, 8, 8);
                    col3.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                    linha.addView(col1);
                    linha.addView(col2);
                    linha.addView(col3);

                    layout.addView(linha);
                }

                new android.app.AlertDialog.Builder(this)
                        .setTitle("Em curso")
                        .setView(view)
                        .setPositiveButton("Fechar", null)
                        .show();
            });
        }).start();
    }

    private void sincronizar_cadastros() {
        // Usar ProgressBar/AlertDialog custom no lugar de ProgressDialog
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Enviando dados... Aguarde.");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            List<RegistroHorasMaquinasModel> cadastros = db.registroHorasMaquinasDao().pegar_registros_finalizados();

            if (cadastros == null || cadastros.isEmpty()) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                            .setTitle("OPS")
                            .setMessage("Não há registros para sincronizar!")
                            .setIcon(R.drawable.ic_atencao)
                            .setPositiveButton("OK", null)
                            .create().show();
                });
                return;
            }

            SincronizacaoRequest request = new SincronizacaoRequest(cadastros);
            service.sincronizarTudo(request).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    progressDialog.dismiss();

                    if (response.isSuccessful()) {
                        // Atualiza todos de uma vez
                        new Thread(() -> {
                            for (RegistroHorasMaquinasModel c : cadastros) {
                                c.sit = "sincronizado";
                            }
                            db.registroHorasMaquinasDao().updateAll(cadastros);
                        }).start();

                        new android.app.AlertDialog.Builder(MainActivity.this)
                                .setTitle("Sucesso")
                                .setMessage("Foram sincronizados " + cadastros.size() + " registros!")
                                .setIcon(R.drawable.ic_success)
                                .setPositiveButton("OK", null)
                                .create().show();
                    } else {
                        try {
                            String erroMsg = response.errorBody() != null
                                    ? response.errorBody().string()
                                    : "Erro desconhecido";
                            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                                    .setTitle("ERRO")
                                    .setMessage("Falha: " + erroMsg)
                                    .setIcon(R.drawable.ic_erro)
                                    .setPositiveButton("OK", null)
                                    .create().show();
                        } catch (Exception e) {
                            showToastErro("Erro ao ler resposta: " + e.getMessage());
                        }
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    progressDialog.dismiss();
                    showToastErro("Falha na comunicação: " + t.getMessage());
                }
            });
        }).start();
    }

    private void zerar_dados_sincronizados() {
        new Thread(() -> {
            try {
                db.registroHorasMaquinasDao().apagar_sincronizados();

                runOnUiThread(() -> {
                    try {
                        showToast("Sincronizados apagados!");
                        if (dialog != null && dialog.isShowing()) {
                            dialog.dismiss();
                        }
                    } catch (Exception uiEx) {
                        Log.e("ZERAR_DADOS", "Erro ao atualizar a UI", uiEx);
                    }
                });

            } catch (Exception dbEx) {
                Log.e("ZERAR_DADOS", "Erro ao apagar sincronizados", dbEx);
                runOnUiThread(() ->
                        showToastErro(dbEx.getMessage())
                );
            }
        }).start();
    }

    private EncarregadoModel pegar_encarregado() {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<EncarregadoModel> future = executor.submit(() ->
                db.encarregadoDAO().pegar_encarregado()
        );

        try {
            return future.get(); // aguarda até ter o resultado
        } catch (Exception e) {
            showToastErro("Erro ao buscar encarregado" + e);
            return null;
        } finally {
            executor.shutdown();
        }
    }

}
