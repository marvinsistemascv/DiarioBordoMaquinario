package marvin.com.br.diariobordomaquinario;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import marvin.com.br.diariobordomaquinario.DAO.AppDatabase;
import marvin.com.br.diariobordomaquinario.Model.EncarregadoModel;
import marvin.com.br.diariobordomaquinario.Model.RegistroHorasMaquinasModel;
import marvin.com.br.diariobordomaquinario.util.DataHora;

public class MainActivity extends AppCompatActivity {

    private TextView txtVersao;
    private AppDatabase db;
    private String encarregado;
    private AlertDialog dialog;                         // <— diálogo atual
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean pediuCadastroEncarregado = false;   // evita abrir 2x

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
        txtVersao = findViewById(R.id.txtVersao);
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
            showToast("ERRO DB: " + e.getMessage());
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
                    encarregado = finalE.nome;
                    showToast("Bem vindo " + finalE.nome);
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
        Button btnAdicionar = viewInflated.findViewById(R.id.btnGravaEncarregado);
        Button btnCancelar = viewInflated.findViewById(R.id.btnCancelarEncarregado);

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
            if (nome.isEmpty()) {
                inputNome.setError("Informe o nome");
                return;
            }

            btnAdicionar.setEnabled(false);
            btnCancelar.setEnabled(false);

            io.execute(() -> {
                try {
                    EncarregadoModel e = new EncarregadoModel();
                    e.nome = nome;
                    e.id = 1;
                    db.encarregadoDAO().inserir(e);

                    runOnUiThread(() -> {
                        if (dialog != null && dialog.isShowing()) dialog.dismiss();
                        showToast("Bem-vindo " + e.nome);
                        // se sua intenção é sair da Main após cadastrar, finalize AQUI (seguro):
                        // if (!isFinishing() && !isDestroyed()) finish();
                    });
                } catch (Exception ex) {
                    runOnUiThread(() -> {
                        btnAdicionar.setEnabled(true);
                        btnCancelar.setEnabled(true);
                        showToast("Erro ao salvar: " + ex.getMessage());
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

        if (id == R.id.menu_option_5) {
            ImageView imageView = new ImageView(this);
            imageView.setImageResource(R.drawable.qrcode);
            imageView.setPadding(20, 20, 20, 20);

            new AlertDialog.Builder(this)
                    .setTitle("Baixe o app com o QR Code")
                    .setView(imageView)
                    .setPositiveButton("Fechar", (d, which) -> d.dismiss())
                    .show();
            return true;
        }

        if (id == R.id.menu_option_4) {
            new AlertDialog.Builder(this)
                    .setTitle("Ressincronizar")
                    .setMessage("Deseja ressincronizar os dados?")
                    .setPositiveButton("Sim", (d, which) -> {
                        // TODO: ressincronizar
                    })
                    .setNegativeButton("Cancelar", (d, which) -> d.dismiss())
                    .show();
            return true;
        }

        return super.onOptionsItemSelected(item);
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

                }
            } catch (Exception ex) {
                RegistroHorasMaquinasModel finalUltima = ultima;
                runOnUiThread(() -> showToast("Erro ao buscar última corrida: " + ex.getMessage()));
            }

            RegistroHorasMaquinasModel finalUltima = ultima;

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalUltima.sit.equals("nulo") || finalUltima.sit.equals("finalizado") ) {
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
        // impede fechar tocando fora
        //dialog.setCanceledOnTouchOutside(false);
        // impede fechar pelo botão "voltar"
       // dialog.setCancelable(false);

//        btnCancelar.setOnClickListener(v -> {
//            if (dialog != null && dialog.isShowing()) dialog.dismiss();
//        });

        btnAdicionar.setOnClickListener(v -> {
            String operador = inputOperador.getText().toString().trim();
            String horimentro = inputQtdHoras.getText().toString().trim();

            if (operador.isEmpty()) {
                inputOperador.setError("Informe a operador");
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
                    r.encarregado = encarregado;
                    r.operador = operador;
                    r.sit = "em curso";
                    r.qtd_horas_inicio = Double.parseDouble(horimentro.replaceAll("\\.", "").replace(",", "."));
                    db.registroHorasMaquinasDao().inserir(r);

                    runOnUiThread(() -> {

                        if (dialog.isShowing()) dialog.dismiss();
                        showToast("Registro feito");
                        if (!isFinishing() && !isDestroyed()) finish();
                    });
                } catch (Exception ex) {
                    runOnUiThread(() -> {
                        btnAdicionar.setEnabled(true);
                        //btnCancelar.setEnabled(true);
                        showToast("Erro ao salvar: " + ex.getMessage());
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
        //Button btnCancelar = viewInflated.findViewById(R.id.btnCancelarRegistro);

        dialog = new AlertDialog.Builder(this)
                .setView(viewInflated)
                .create();

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        // impede fechar tocando fora
        //dialog.setCanceledOnTouchOutside(false);
        // impede fechar pelo botão "voltar"
        //dialog.setCancelable(false);

//        btnCancelar.setOnClickListener(v -> {
//            if (dialog != null && dialog.isShowing()) dialog.dismiss();
//        });

        btnAdicionar.setOnClickListener(v -> {
            String horimetro = inputHorimetro.getText().toString().trim();
            if (horimetro.isEmpty()) {
                inputHorimetro.setError("Informe o horímetro");
                return;
            }

            btnAdicionar.setEnabled(false);
            //btnCancelar.setEnabled(false);

            io.execute(() -> {
                try {
                    r.sit = "finalizado";
                    r.data_termino = DataHora.data_atual();
                    r.hora_termino = DataHora.pegar_hora();
                    r.qtd_horas_final = Double.parseDouble(horimetro);
                    db.registroHorasMaquinasDao().inserir(r);

                    runOnUiThread(() -> {
                        if (dialog != null && dialog.isShowing()) dialog.dismiss();
                        showToast("Registro finalizado");
                        // se sua intenção é sair da Main após cadastrar, finalize AQUI (seguro):
                        // if (!isFinishing() && !isDestroyed()) finish();
                    });
                } catch (Exception ex) {
                    runOnUiThread(() -> {
                        btnAdicionar.setEnabled(true);
                        //btnCancelar.setEnabled(true);
                        showToast("Erro ao finalizar: " + ex.getMessage());
                    });
                }
            });
        });

    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void exibirDialogComListaEmCurso() {
        new Thread(() -> {
            List<RegistroHorasMaquinasModel> lista = db.registroHorasMaquinasDao().pegar_corridas_nao_sincronizadas();
            runOnUiThread(() -> {
                LayoutInflater inflater = LayoutInflater.from(this);
                View view = inflater.inflate(R.layout.dialog_lista_registros, null);
                LinearLayout layout = view.findViewById(R.id.layout_lista_registros);

                for (RegistroHorasMaquinasModel prop : lista) {
                    TextView item = new TextView(this);
                    item.setText("máquina:" + prop.cod_maquina + " - " + prop.operador);
                    item.setPadding(8, 8, 8, 8);
                    item.setTextSize(16f);
                    layout.addView(item);
                }
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Em curso-" + encarregado)
                        .setView(view)
                        .setPositiveButton("Fechar", null)
                        .show();
            });
        }).start();
    }
}
