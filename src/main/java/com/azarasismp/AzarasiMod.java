package com.azarasismp;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AzarasiMod implements ModInitializer {
    public static final String MOD_ID = "azarasismp";
    private static final Map<UUID, Long> pvpMessageCooldown = new HashMap<>();
    
    // TPS計測用
    private static long lastTickTime = System.currentTimeMillis();
    private static double currentTps = 20.0;
    private static int tickCount = 0;

    @Override
    public void onInitialize() {
        System.out.println("🦭 [AzarasiSMP] MODをロード中...");

        AzarasiManager.loadHomes();

        // TPS計算ループ
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCount++;
            if (tickCount % 20 == 0) {
                long now = System.currentTimeMillis();
                long diff = now - lastTickTime;
                lastTickTime = now;
                currentTps = Math.min(20.0, 20000.0 / Math.max(1, diff));
                
                // スコアボードのTPS値を更新
                Scoreboard sb = server.getScoreboard();
                ScoreboardObjective obj = sb.getNullableObjective("azarasi_tps");
                if (obj != null) {
                    ScoreboardScore score = sb.getOrCreateScore(ScoreHolder.fromName("TPS"), obj);
                    score.setScore((int) currentTps);
                }
            }
        });

        // PvP攻撃キャンセル & クールダウン処理
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity victim && source.getAttacker() instanceof ServerPlayerEntity attacker) {
                boolean attackerPvp = AzarasiManager.isPvpEnabled(attacker.getUuid());
                boolean victimPvp = AzarasiManager.isPvpEnabled(victim.getUuid());

                if (!attackerPvp || !victimPvp) {
                    long now = System.currentTimeMillis();
                    long lastSent = pvpMessageCooldown.getOrDefault(attacker.getUuid(), 0L);
                    
                    if (now - lastSent > 5000) { // 5秒のクールダウン
                        pvpMessageCooldown.put(attacker.getUuid(), now);
                        if (!victimPvp) {
                            attacker.sendMessage(Text.literal("§c[AzarasiSMP] 相手はPvPをオフにしています！"), false);
                        } else {
                            attacker.sendMessage(Text.literal("§c[AzarasiSMP] あなたのPvPがオフになっています！ (/pvp でオンにできます)"), false);
                        }
                    }
                    return false; // 攻撃無効化
                }
            }
            return true;
        });

        // コマンドの登録
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            
            // /tps
            dispatcher.register(CommandManager.literal("tps").executes(ctx -> {
                String color = currentTps > 18.0 ? "§a" : currentTps > 15.0 ? "§e" : "§c";
                ctx.getSource().sendFeedback(() -> Text.literal("§b[AzarasiSMP] §f現在のTPS: " + color + String.format("%.2f", currentTps)), false);
                return 1;
            }));

            // /tpa & /tp
            for (String cmd : new String[]{"tpa", "tp"}) {
                dispatcher.register(CommandManager.literal(cmd)
                    .executes(ctx -> {
                        AzarasiGui.openTpaGui(ctx.getSource().getPlayerOrThrow(), null, false);
                        return 1;
                    })
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(ctx -> {
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                            AzarasiGui.openTpaGui(ctx.getSource().getPlayerOrThrow(), target.getGameProfile().getName(), false);
                            return 1;
                        }))
                );
            }

            // /tpahere & /tphere
            for (String cmd : new String[]{"tpahere", "tphere"}) {
                dispatcher.register(CommandManager.literal(cmd)
                    .executes(ctx -> {
                        AzarasiGui.openTpaGui(ctx.getSource().getPlayerOrThrow(), null, true);
                        return 1;
                    })
                    .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(ctx -> {
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                            AzarasiGui.openTpaGui(ctx.getSource().getPlayerOrThrow(), target.getGameProfile().getName(), true);
                            return 1;
                        }))
                );
            }

            // /tpaccept
            dispatcher.register(CommandManager.literal("tpaccept")
                .then(CommandManager.argument("sender", StringArgumentType.string())
                    .executes(ctx -> {
                        String senderName = StringArgumentType.getString(ctx, "sender");
                        AzarasiManager.acceptTpa(ctx.getSource().getPlayerOrThrow(), senderName);
                        return 1;
                    }))
            );

            // /tpadeny
            dispatcher.register(CommandManager.literal("tpadeny")
                .then(CommandManager.argument("sender", StringArgumentType.string())
                    .executes(ctx -> {
                        String senderName = StringArgumentType.getString(ctx, "sender");
                        AzarasiManager.denyTpa(ctx.getSource().getPlayerOrThrow(), senderName);
                        return 1;
                    }))
            );

            // /rtp
            dispatcher.register(CommandManager.literal("rtp").executes(ctx -> {
                AzarasiGui.openRtpGui(ctx.getSource().getPlayerOrThrow());
                return 1;
            }));

            // /sethome
            dispatcher.register(CommandManager.literal("sethome")
                .executes(ctx -> {
                    AzarasiGui.openHomeGui(ctx.getSource().getPlayerOrThrow());
                    return 1;
                })
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name");
                        AzarasiManager.setHome(ctx.getSource().getPlayerOrThrow(), name);
                        return 1;
                    }))
            );

            // /delhome
            dispatcher.register(CommandManager.literal("delhome")
                .executes(ctx -> {
                    AzarasiGui.openHomeGui(ctx.getSource().getPlayerOrThrow());
                    return 1;
                })
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name");
                        AzarasiManager.delHome(ctx.getSource().getPlayerOrThrow(), name);
                        return 1;
                    }))
            );

            // /home
            dispatcher.register(CommandManager.literal("home")
                .executes(ctx -> {
                    AzarasiGui.openHomeGui(ctx.getSource().getPlayerOrThrow());
                    return 1;
                })
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name");
                        AzarasiManager.tpHome(ctx.getSource().getPlayerOrThrow(), name);
                        return 1;
                    }))
            );

            // /pvp
            dispatcher.register(CommandManager.literal("pvp").executes(ctx -> {
                AzarasiGui.openPvpGui(ctx.getSource().getPlayerOrThrow());
                return 1;
            }));

            // あざらしSMP スコアボード自動セットアップコマンド (/azarasismp_sb)
            dispatcher.register(CommandManager.literal("azarasismp_sb").executes(ctx -> {
                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                Scoreboard sb = p.getServer().getScoreboard();
                
                ScoreboardObjective obj = sb.getNullableObjective("azarasi_sb");
                if (obj != null) sb.removeObjective(obj);

                obj = sb.addObjective("azarasi_sb", ScoreboardCriterion.DUMMY, Text.literal("§b§lあざらしSMP"), ScoreboardCriterion.RenderType.INTEGER);
                sb.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, obj);

                ScoreboardScore scoreDiscord = sb.getOrCreateScore(ScoreHolder.fromName("§ehttps://discord.gg/ZmKjZXjVSW"), obj);
                scoreDiscord.setScore(1);

                p.sendMessage(Text.literal("§a[AzarasiSMP] スコアボードを設定したよ！"), false);
                return 1;
            }));
        });
    }

    public static double getCurrentTps() { return currentTps; }
}
