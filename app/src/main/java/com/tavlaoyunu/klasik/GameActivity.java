package com.tavlaoyunu.klasik;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.tavlaoyunu.klasik.engine.Game;
import com.tavlaoyunu.klasik.engine.Move;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameActivity extends Activity implements SensorEventListener {
    private Game game;
    private int level = 2;
    private BoardView bv;
    private BoardTheme theme;
    private TextView tvCpu, tvYou, tvStatus, tvCredits;
    private LinearLayout cardCpu, cardYou;
    private TextView tvSay;
    private final java.util.Random say = new java.util.Random();
    private Button btnUndo, btnHint, btnRoll;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private boolean destroyed = false;
    private boolean busy = false;
    private int freeUndo = 1;
    private int freeHint = 1;
    private boolean finished = false;
    private int humanHits = 0;

    private SensorManager sm;
    private Sensor accel;
    private long lastShake = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        level = getIntent().getIntExtra("level", 2);
        int tid = Prefs.theme();
        theme = BoardTheme.get(tid);
        if (!Economy.owns("b" + theme.id)) theme = BoardTheme.get(0);

        LinearLayout root = UiKit.vertical(this);
        root.setBackgroundColor(theme.bg);
        UiKit.applyInsets(root, 8);

        tvCpu = UiKit.text(this, "", 15, theme.text, true);
        cardCpu = makeCard(level == 1 ? "🧒" : (level == 3 ? "🎩" : "🧔"), tvCpu);
        tvSay = UiKit.text(this, "", 14, 0xFF222222, true);
        tvSay.setBackground(UiKit.round(0xFFFFF3D6, 14, this));
        int bp = UiKit.dp(this, 8);
        tvSay.setPadding(bp * 2, bp / 2, bp * 2, bp / 2);
        tvSay.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sl.setMargins(UiKit.dp(this, 10), 0, 0, 0);
        sl.gravity = Gravity.CENTER_VERTICAL;
        tvSay.setGravity(Gravity.CENTER);
        cardCpu.addView(tvSay, sl);
        root.addView(cardCpu, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bv = new BoardView(this);
        bv.setTheme(theme);
        bv.setSkins(Economy.equipped(Economy.DICE), Economy.equipped(Economy.CHECKER));
        bv.setListener(new BoardView.Listener() {
            @Override
            public void onRollRequested() {
                requestRoll();
            }

            @Override
            public void onMoveChosen(Move m) {
                if (!busy) doHumanMove(m);
            }
        });
        root.addView(bv, UiKit.lpWeight(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        tvYou = UiKit.text(this, "", 15, theme.text, true);
        cardYou = makeCard(Economy.equipped(Economy.AVATAR).emoji, tvYou);
        root.addView(cardYou, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvStatus = UiKit.text(this, "", 17, Color.BLACK, true);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setBackground(UiKit.round(theme.accent, 22, this));
        int sp = UiKit.dp(this, 8);
        tvStatus.setPadding(sp * 2, sp, sp * 2, sp);
        LinearLayout.LayoutParams slp = UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 2));
        root.addView(tvStatus, slp);

        tvCredits = UiKit.text(this, "", 12, UiKit.MUTED, false);
        tvCredits.setGravity(Gravity.CENTER);
        root.addView(tvCredits, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = UiKit.horizontal(this);
        row.setGravity(Gravity.CENTER);
        btnUndo = UiKit.button(this, getString(R.string.undo), UiKit.PANEL, UiKit.CREAM);
        btnHint = UiKit.button(this, getString(R.string.hint), UiKit.PANEL, UiKit.CREAM);
        btnRoll = UiKit.button(this, getString(R.string.roll_dice), theme.accent, Color.BLACK);
        int m = UiKit.dp(this, 4);
        LinearLayout.LayoutParams l1 = UiKit.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.setMargins(m, 0, m, 0);
        LinearLayout.LayoutParams l2 = UiKit.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l2.setMargins(m, 0, m, 0);
        LinearLayout.LayoutParams l3 = UiKit.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f);
        l3.setMargins(m, 0, m, 0);
        row.addView(btnUndo, l1);
        row.addView(btnHint, l2);
        row.addView(btnRoll, l3);
        root.addView(row, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        btnUndo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onUndo();
            }
        });
        btnHint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onHint();
            }
        });
        btnRoll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestRoll();
            }
        });

        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accel = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        startNewGame();
    }

    private LinearLayout makeCard(String emoji, TextView label) {
        LinearLayout card = UiKit.horizontal(this);
        card.setBackground(UiKit.card(this, 0x44000000, 0, 0));
        int p = UiKit.dp(this, 8);
        card.setPadding(p * 2, p, p * 2, p);
        TextView av = UiKit.text(this, emoji, 24, Color.WHITE, false);
        av.setPadding(0, 0, UiKit.dp(this, 10), 0);
        card.addView(av);
        card.addView(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UiKit.dp(this, 2), 0, UiKit.dp(this, 2));
        card.setLayoutParams(lp);
        return card;
    }

    private String oppName() {
        switch (level) {
            case 1: return getString(R.string.opp_1);
            case 3: return getString(R.string.opp_3);
            default: return getString(R.string.opp_2);
        }
    }

    /** Rakip karakter kısa bir söz söyler (konuşma balonu). */
    private void saySomething(int res) {
        if (tvSay == null || destroyed) return;
        tvSay.setText(res);
        tvSay.setVisibility(View.VISIBLE);
        h.removeCallbacks(hideSay);
        h.postDelayed(hideSay, 2600);
    }

    private final Runnable hideSay = new Runnable() {
        @Override
        public void run() {
            if (tvSay != null) tvSay.setVisibility(View.INVISIBLE);
        }
    };

    // ------------------------------------------------------------ oyun akışı

    private void startNewGame() {
        finished = false;
        humanHits = 0;
        freeUndo = 1;
        freeHint = 1;
        game = new Game();
        game.start();
        bv.setGame(game);
        updateInfo();
        updateButtons();
        busy = true;
        tvStatus.setText("");
        saySomething(R.string.say_start);
        Sfx.diceRoll();
        bv.animateDice(new Runnable() {
            @Override
            public void run() {
                Sfx.diceLand();
                afterOpening();
            }
        });
    }

    private void afterOpening() {
        if (destroyed) return;
        if (game.turn == Game.CPU) {
            tvStatus.setText(R.string.opening_cpu);
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!destroyed) cpuHandleRolled();
                }
            }, 1000);
        } else {
            tvStatus.setText(R.string.opening_you);
            busy = false;
            afterHumanRoll();
        }
    }

    private void startHumanTurn() {
        if (destroyed) return;
        busy = false;
        bv.clearSelection();
        tvStatus.setText(Prefs.shake() && accel != null ? R.string.tap_to_roll : R.string.tap_to_roll_noshake);
        updateInfo();
        updateButtons();
    }

    private void requestRoll() {
        if (busy || game == null || game.over || game.turn != Game.HUMAN) return;
        if (game.rolled) {
            Toast.makeText(this, R.string.move_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        busy = true;
        game.roll();
        updateButtons();
        Sfx.diceRoll();
        bv.animateDice(new Runnable() {
            @Override
            public void run() {
                Sfx.diceLand();
                busy = false;
                afterHumanRoll();
            }
        });
    }

    private void afterHumanRoll() {
        if (destroyed) return;
        updateInfo();
        updateButtons();
        if (game.noMoves()) {
            busy = true;
            tvStatus.setText(R.string.no_moves);
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (destroyed) return;
                    game.endTurn();
                    cpuTurn();
                }
            }, 1400);
            return;
        }
        tvStatus.setText(R.string.move_hint);
        maybeAutoPlay();
    }

    /** Tek yasal hamle varsa otomatik oynar. */
    private void maybeAutoPlay() {
        if (busy || game.over) return;
        List<Move> nm = game.nextMoves();
        if (nm.size() == 1) {
            final Move only = nm.get(0);
            busy = true;
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (destroyed) return;
                    busy = false;
                    doHumanMove(only);
                }
            }, 500);
        }
    }

    private void doHumanMove(Move m) {
        busy = true;
        boolean hit = game.play(m);
        if (hit) {
            humanHits++;
            Sfx.hit();
            saySomething(say.nextBoolean() ? R.string.say_got_hit_a : R.string.say_got_hit_b);
        } else Sfx.checker();
        updateButtons();
        bv.animateMove(Game.HUMAN, m, new Runnable() {
            @Override
            public void run() {
                if (destroyed) return;
                updateInfo();
                if (game.over) {
                    onGameOver();
                    return;
                }
                if (game.turnComplete()) {
                    tvStatus.setText(R.string.cpu_thinking);
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (destroyed) return;
                            game.endTurn();
                            cpuTurn();
                        }
                    }, 350);
                } else {
                    busy = false;
                    tvStatus.setText(R.string.move_hint);
                    updateButtons();
                    maybeAutoPlay();
                }
            }
        });
    }

    private void cpuTurn() {
        busy = true;
        bv.clearSelection();
        tvStatus.setText(R.string.cpu_thinking);
        updateButtons();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (destroyed) return;
                game.roll();
                Sfx.diceRoll();
                bv.animateDice(new Runnable() {
                    @Override
                    public void run() {
                        Sfx.diceLand();
                        cpuHandleRolled();
                    }
                });
            }
        }, 500);
    }

    private void cpuHandleRolled() {
        if (destroyed) return;
        busy = true;
        updateInfo();
        updateButtons();
        if (game.noMoves()) {
            tvStatus.setText(R.string.no_moves);
            saySomething(R.string.say_nomove);
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (destroyed) return;
                    game.endTurn();
                    startHumanTurn();
                }
            }, 1400);
            return;
        }
        tvStatus.setText(R.string.cpu_thinking);
        if (game.d1 == game.d2) saySomething(R.string.say_double);
        final int lv = level;
        exec.execute(new Runnable() {
            @Override
            public void run() {
                final List<Move> plan = game.cpuPlan(lv);
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!destroyed) playCpuMoves(plan, 0);
                    }
                });
            }
        });
    }

    private void playCpuMoves(final List<Move> plan, final int i) {
        if (destroyed) return;
        if (i >= plan.size()) {
            if (game.over) {
                onGameOver();
            } else {
                game.endTurn();
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startHumanTurn();
                    }
                }, 300);
            }
            return;
        }
        Move m = plan.get(i);
        boolean hit = game.play(m);
        if (hit) {
            Sfx.hit();
            saySomething(say.nextBoolean() ? R.string.say_hit_a : R.string.say_hit_b);
        } else Sfx.checker();
        bv.animateMove(Game.CPU, m, new Runnable() {
            @Override
            public void run() {
                if (destroyed) return;
                updateInfo();
                if (game.over) {
                    onGameOver();
                } else {
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            playCpuMoves(plan, i + 1);
                        }
                    }, 220);
                }
            }
        });
    }

    // ------------------------------------------------------------ geri al / ipucu / haklar

    /** Hak harca: Pro sınırsız; ücretsizde oyun başına 1 + reklamla kazanılan haklar. */
    private boolean spend(boolean isHint) {
        if (Prefs.pro()) return true;
        if (isHint) {
            if (freeHint > 0) {
                freeHint--;
                return true;
            }
        } else if (freeUndo > 0) {
            freeUndo--;
            return true;
        }
        if (Prefs.spendCredit()) {
            updateButtons();
            return true;
        }
        showNeedCredit();
        return false;
    }

    private void showNeedCredit() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.need_credit_title)
                .setMessage(R.string.need_credit_msg)
                .setPositiveButton(R.string.watch_ad, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        AdManager.showRewarded(GameActivity.this, new Runnable() {
                            @Override
                            public void run() {
                                Prefs.addCredits(3);
                                Toast.makeText(GameActivity.this, R.string.credits_earned, Toast.LENGTH_SHORT).show();
                                updateButtons();
                            }
                        }, new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(GameActivity.this, R.string.ad_not_ready, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .setNeutralButton(R.string.go_pro, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        startActivity(new Intent(GameActivity.this, ProActivity.class));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void onUndo() {
        if (busy || game == null || !game.canUndo()) return;
        if (!spend(false)) return;
        game.undo();
        bv.clearSelection();
        updateInfo();
        updateButtons();
        tvStatus.setText(R.string.move_hint);
    }

    private void onHint() {
        if (busy || game == null || !game.rolled || game.turn != Game.HUMAN || game.noMoves()) return;
        if (!spend(true)) return;
        Move hm = game.hint();
        if (hm == null) {
            Toast.makeText(this, R.string.hint_none, Toast.LENGTH_SHORT).show();
        } else {
            bv.showHint(hm);
        }
        updateButtons();
    }

    // ------------------------------------------------------------ bilgi çubukları

    private void updateInfo() {
        if (game == null) return;
        tvCpu.setText(oppName() + " · " + getString(R.string.cpu) + "\n" + getString(R.string.pip_fmt,
                game.board.pip(Game.CPU), game.board.off[Game.CPU]));
        tvYou.setText(Economy.name() + "\n" + getString(R.string.pip_fmt,
                game.board.pip(Game.HUMAN), game.board.off[Game.HUMAN]));
        boolean humanTurn = game.turn == Game.HUMAN && !game.over;
        cardYou.setBackground(UiKit.card(this, humanTurn ? 0x66000000 : 0x33000000, theme.accent, humanTurn ? 2 : 0));
        cardCpu.setBackground(UiKit.card(this, !humanTurn ? 0x66000000 : 0x33000000, theme.accent, !humanTurn ? 2 : 0));
    }

    private void updateButtons() {
        if (game == null) return;
        boolean human = game.turn == Game.HUMAN && !game.over;
        UiKit.setEnabledLook(btnUndo, human && !busy && game.canUndo());
        UiKit.setEnabledLook(btnHint, human && !busy && game.rolled && !game.noMoves());
        UiKit.setEnabledLook(btnRoll, human && !busy && !game.rolled);
        btnRoll.setText(human && game.rolled ? R.string.btn_move : (human || game.over ? R.string.roll_dice : R.string.btn_wait));
        if (Prefs.pro()) {
            tvCredits.setText("");
            tvCredits.setVisibility(View.GONE);
        } else {
            tvCredits.setVisibility(View.VISIBLE);
            tvCredits.setText(getString(R.string.credits_fmt, Prefs.credits() + freeHint + freeUndo));
        }
    }

    // ------------------------------------------------------------ oyun sonu

    private void onGameOver() {
        if (finished) return;
        finished = true;
        busy = true;
        updateButtons();
        final boolean humanWon = game.winner == Game.HUMAN;
        final boolean gammon = game.winType == 1;
        Prefs.recordGame(level, humanWon, gammon, game.faceCount, game.doubles, game.rollCount);
        Prefs.finishedThisSession++;
        Economy.Reward rw = Economy.onGameFinished(level, humanWon, gammon, humanHits, game.doubles[Game.HUMAN]);
        if (humanWon) Sfx.win();
        else Sfx.lose();
        saySomething(humanWon ? R.string.say_lose : R.string.say_win);

        LinearLayout box = UiKit.vertical(this);
        int pad = UiKit.dp(this, 18);
        box.setPadding(pad, pad, pad, UiKit.dp(this, 8));
        box.setBackgroundColor(UiKit.PANEL);

        TextView title = UiKit.text(this, getString(humanWon ? R.string.win_title : R.string.lose_title), 22,
                humanWon ? UiKit.GOLD : UiKit.CREAM, true);
        title.setGravity(Gravity.CENTER);
        box.addView(title);
        if (gammon) {
            TextView g = UiKit.text(this, getString(R.string.gammon_note), 16, UiKit.GOLD, true);
            g.setGravity(Gravity.CENTER);
            box.addView(g);
        }
        TextView rewardTv = UiKit.text(this, "+" + rw.coins + " 🪙    +" + rw.xp + " XP", 20, UiKit.GOLD, true);
        rewardTv.setGravity(Gravity.CENTER);
        rewardTv.setPadding(0, UiKit.dp(this, 8), 0, 0);
        box.addView(rewardTv);
        if (rw.levelUp) {
            TextView lv = UiKit.text(this, Economy.T("🎉 Seviye atladın: ", "🎉 Level up: ") + rw.newLevel + Economy.T("  (+50 🪙 bonus)", "  (+50 🪙 bonus)"), 15, UiKit.CREAM, true);
            lv.setGravity(Gravity.CENTER);
            box.addView(lv);
        }
        if (Economy.anythingToClaim()) {
            TextView ms = UiKit.text(this, Economy.T("🎯 Alınacak görev ödülün var (Menü > Görevler)", "🎯 You have mission rewards to claim (Menu > Missions)"), 12, UiKit.MUTED, false);
            ms.setGravity(Gravity.CENTER);
            box.addView(ms);
        }
        box.addView(UiKit.spacer(this, 10));

        TextView st = UiKit.text(this, getString(R.string.dice_fair_title), 15, UiKit.CREAM, true);
        box.addView(st);

        LinearLayout legend = UiKit.horizontal(this);
        legend.addView(UiKit.text(this, "■ " + getString(R.string.legend_you) + "   ", 13, UiKit.GOLD, true));
        legend.addView(UiKit.text(this, "■ " + getString(R.string.legend_cpu), 13, 0xFF90A4AE, true));
        box.addView(legend);

        DiceStatsView dv = new DiceStatsView(this);
        dv.setData(game.faceCount[Game.HUMAN], game.faceCount[Game.CPU]);
        box.addView(dv, UiKit.lp(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 170)));

        box.addView(UiKit.text(this, getString(R.string.legend_you) + ": " + getString(R.string.dice_doubles_fmt,
                game.doubles[Game.HUMAN], avg(Game.HUMAN)), 13, UiKit.CREAM, false));
        box.addView(UiKit.text(this, getString(R.string.legend_cpu) + ": " + getString(R.string.dice_doubles_fmt,
                game.doubles[Game.CPU], avg(Game.CPU)), 13, UiKit.CREAM, false));
        TextView note = UiKit.text(this, getString(R.string.dice_fair_note), 12, UiKit.MUTED, false);
        note.setPadding(0, UiKit.dp(this, 6), 0, 0);
        box.addView(note);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setView(box)
                .setCancelable(false)
                .setPositiveButton(R.string.new_game, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        afterAd(new Runnable() {
                            @Override
                            public void run() {
                                startNewGame();
                            }
                        });
                    }
                })
                .setNegativeButton(R.string.menu, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        afterAd(new Runnable() {
                            @Override
                            public void run() {
                                finish();
                            }
                        });
                    }
                })
                .create();
        dlg.show();
    }

    private String avg(int player) {
        if (game.rollCount[player] == 0) return "-";
        return String.format(Locale.US, "%.1f", game.pipRolled[player] / (double) game.rollCount[player]);
    }

    /** İkinci bitirilen oyundan itibaren (Pro değilse) geçiş reklamı göster. */
    private void afterAd(Runnable next) {
        if (!Prefs.pro() && Prefs.finishedThisSession >= 2) {
            AdManager.showInterstitial(this, next);
        } else {
            next.run();
        }
    }

    // ------------------------------------------------------------ yaşam döngüsü

    @Override
    public void onBackPressed() {
        if (game == null || game.over || finished) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.exit_title)
                .setMessage(R.string.exit_msg)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        finish();
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sm != null && accel != null && Prefs.shake()) {
            sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
        }
        if (game != null) updateButtons();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sm != null) sm.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        h.removeCallbacksAndMessages(null);
        exec.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float gx = e.values[0] / SensorManager.GRAVITY_EARTH;
        float gy = e.values[1] / SensorManager.GRAVITY_EARTH;
        float gz = e.values[2] / SensorManager.GRAVITY_EARTH;
        double g = Math.sqrt(gx * gx + gy * gy + gz * gz);
        long now = System.currentTimeMillis();
        if (g > 2.6 && now - lastShake > 1200) {
            lastShake = now;
            requestRoll();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
