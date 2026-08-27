package com.azarasismp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

public class AzarasiManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_DIR = new File("config/azarasismp");
    private static final File HOMES_FILE = new File(CONFIG_DIR, "homes.json");

    // UUID -> HomeName -> Location
    private static Map<String, Map<String, HomeLoc>> homesMap = new HashMap<>();
    private static final Map<UUID, Boolean> pvpStatus = new HashMap<>();
    
    // TPAリクエスト保持: Target UUID -> RequestInfo
    public static final Map<UUID, TpaRequest> pendingTpa = new HashMap<>();

    public record HomeLoc(double x, double y, double z, float yaw, float pitch, String dimension) {}
    public record TpaRequest(UUID senderUuid, String senderName, boolean isHere, long time) {}

    public static void loadHomes() {
        try {
            if (!CONFIG_DIR.exists()) CONFIG_DIR.mkdirs();
            if (HOMES_FILE.exists()) {
                FileReader reader = new FileReader(HOMES_FILE);
                Type type = new TypeToken<Map<String, Map<String, HomeLoc>>>(){}.getType();
                homesMap = GSON.fromJson(reader, type);
                reader.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveHomes() {
        try {
            if (!CONFIG_DIR.exists()) CONFIG_DIR.mkdirs();
            FileWriter writer = new FileWriter(HOMES_FILE);
            GSON.toJson(homesMap, writer);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Home関連機能
    public static void setHome(ServerPlayerEntity player, String name) {
        String uuid = player.getUuidAsString();
        homesMap.putIfAbsent(uuid, new HashMap<>());
        HomeLoc loc = new HomeLoc(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(), player.getWorld().getRegistryKey().getValue().toString());
        homesMap.get(uuid).put(name, loc);
        saveHomes();
        player.sendMessage(Text.literal("§a[AzarasiSMP] ホーム 『" + name + "』 を保存したよ！"), false);
    }

    public static void delHome(ServerPlayerEntity player, String name) {
        String uuid = player.getUuidAsString();
        if (homesMap.containsKey(uuid) && homesMap.get(uuid).containsKey(name)) {
            homesMap.get(uuid).remove(name);
            saveHomes();
            player.sendMessage(Text.literal("§c[AzarasiSMP] ホーム 『" + name + "』 を削除したよ！"), false);
        } else {
            player.sendMessage(Text.literal("§c[AzarasiSMP] その名前のホームは見つからないよ。"), false);
        }
    }

    public static void tpHome(ServerPlayerEntity player, String name) {
        String uuid = player.getUuidAsString();
        if (homesMap.containsKey(uuid) && homesMap.get(uuid).containsKey(name)) {
            HomeLoc loc = homesMap.get(uuid).get(name);
            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, new Identifier(loc.dimension()));
            ServerWorld targetWorld = player.getServer().getWorld(key);
            if (targetWorld != null) {
                player.teleport(targetWorld, loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
                player.sendMessage(Text.literal("§a[AzarasiSMP] ホーム 『" + name + "』 にテレポートしたよ！"), false);
            }
        } else {
            player.sendMessage(Text.literal("§c[AzarasiSMP] その名前のホームは見つからないよ。"), false);
        }
    }

    public static Map<String, HomeLoc> getPlayerHomes(ServerPlayerEntity player) {
        return homesMap.getOrDefault(player.getUuidAsString(), Collections.emptyMap());
    }

    // PvPオンオフ
    public static boolean isPvpEnabled(UUID uuid) {
        return pvpStatus.getOrDefault(uuid, false);
    }

    public static void togglePvp(UUID uuid) {
        pvpStatus.put(uuid, !isPvpEnabled(uuid));
    }

    // TPA リクエスト送信
    public static void sendTpaRequest(ServerPlayerEntity sender, ServerPlayerEntity target, boolean isHere) {
        pendingTpa.put(target.getUuid(), new TpaRequest(sender.getUuid(), sender.getGameProfile().getName(), isHere, System.currentTimeMillis()));

        sender.sendMessage(Text.literal("§a[AzarasiSMP] §e" + target.getGameProfile().getName() + " §aに" + (isHere ? "TPAHERE" : "TPA") + "申請を送ったよ！"), false);

        // カラフルなクリック可能チャットメッセージの生成
        String reqType = isHere ? "tpahere" : "tpa";
        MutableText msg = Text.literal("\n§b----------------------------------------\n")
                .append(Text.literal("§e" + sender.getGameProfile().getName() + " §aから§c" + reqType.toUpperCase() + "申請§aが届いています！\n"))
                .append(Text.literal("許可または拒否をクリックしてね:\n\n"));

        MutableText acceptBtn = Text.literal("【 許可する 】")
                .formatted(Formatting.GREEN, Formatting.BOLD)
                .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept " + sender.getGameProfile().getName()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§aクリックで許可"))));

        MutableText denyBtn = Text.literal("   【 拒否する 】")
                .formatted(Formatting.RED, Formatting.BOLD)
                .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpadeny " + sender.getGameProfile().getName()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§cクリックで拒否"))));

        msg.append(acceptBtn).append(denyBtn).append(Text.literal("\n§b----------------------------------------\n"));
        target.sendMessage(msg, false);
    }

    public static void acceptTpa(ServerPlayerEntity target, String senderName) {
        TpaRequest req = pendingTpa.get(target.getUuid());
        if (req != null && req.senderName().equalsIgnoreCase(senderName)) {
            ServerPlayerEntity sender = target.getServer().getPlayerManager().getPlayer(req.senderUuid());
            if (sender != null) {
                if (req.isHere()) {
                    target.teleport(sender.getServerWorld(), sender.getX(), sender.getY(), sender.getZ(), sender.getYaw(), sender.getPitch());
                } else {
                    sender.teleport(target.getServerWorld(), target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
                }
                target.sendMessage(Text.literal("§a[AzarasiSMP] 申請を許可したよ！"), false);
                sender.sendMessage(Text.literal("§a[AzarasiSMP] 申請が許可されたよ！"), false);
            }
            pendingTpa.remove(target.getUuid());
        } else {
            target.sendMessage(Text.literal("§c[AzarasiSMP] 該当する有効な申請が見つからないよ。"), false);
        }
    }

    public static void denyTpa(ServerPlayerEntity target, String senderName) {
        TpaRequest req = pendingTpa.get(target.getUuid());
        if (req != null && req.senderName().equalsIgnoreCase(senderName)) {
            ServerPlayerEntity sender = target.getServer().getPlayerManager().getPlayer(req.senderUuid());
            if (sender != null) {
                sender.sendMessage(Text.literal("§c[AzarasiSMP] 申請が拒否されました。"), false);
            }
            target.sendMessage(Text.literal("§c[AzarasiSMP] 申請を拒否したよ。"), false);
            pendingTpa.remove(target.getUuid());
        }
    }

    // 安全なランダムテレポート (RTP)
    public static void executeRtp(ServerPlayerEntity player, RegistryKey<World> dimKey) {
        ServerWorld world = player.getServer().getWorld(dimKey);
        if (world == null) return;

        Random random = new Random();
        player.sendMessage(Text.literal("§e[AzarasiSMP] 安全なスポーン地を探しているよ..."), false);

        for (int attempts = 0; attempts < 50; attempts++) {
            int x = (random.nextInt(3000) - 1500) + (int) player.getX();
            int z = (random.nextInt(3000) - 1500) + (int) player.getZ();

            int startY = dimKey == World.NETHER ? 110 : 250;
            int minY = dimKey == World.NETHER ? 32 : (dimKey == World.END ? 40 : -50);

            for (int y = startY; y > minY; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = world.getBlockState(pos);
                BlockState feet = world.getBlockState(pos.up());
                BlockState head = world.getBlockState(pos.up(2));

                // 足場が固く、身体2マスが空気（マグマ・奈落回避）
                if (state.isOpaqueFullCube(world, pos) && feet.isAir() && head.isAir()) {
                    if (dimKey == World.NETHER && y >= 120) continue; // ネザー天井岩盤回避

                    player.teleport(world, x + 0.5, y + 1.0, z + 0.5, player.getYaw(), player.getPitch());
                    player.sendMessage(Text.literal("§a[AzarasiSMP] 安全な場所にランダムテレポートしたよ！"), false);
                    return;
                }
            }
        }
        player.sendMessage(Text.literal("§c[AzarasiSMP] 安全なテレポート先が見つからなかったよ。もう一度試してみてね！"), false);
    }
}
