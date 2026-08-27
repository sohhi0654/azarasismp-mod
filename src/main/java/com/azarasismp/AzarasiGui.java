package com.azarasismp;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ChestScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AzarasiGui {

    // RTP 選択 GUI
    public static void openRtpGui(ServerPlayerEntity player) {
        SimpleInventory inv = new SimpleInventory(27);
        
        inv.setStack(11, createItem(Items.GRASS_BLOCK, "§aオーバーワールドへRTP"));
        inv.setStack(13, createItem(Items.NETHERRACK, "§cネザーへRTP"));
        inv.setStack(15, createItem(Items.END_STONE, "§eエンドへRTP"));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, p) -> 
            new CustomChestHandler(ScreenHandlerType.GENERIC_9X3, syncId, playerInv, inv, 3, (slot, pEntity) -> {
                if (slot == 11) AzarasiManager.executeRtp(pEntity, World.OVERWORLD);
                if (slot == 13) AzarasiManager.executeRtp(pEntity, World.NETHER);
                if (slot == 15) AzarasiManager.executeRtp(pEntity, World.END);
                pEntity.closeHandledScreen();
            }), Text.literal("§b🦭 RTP ディメンション選択")));
    }

    // TPA / TPAHere GUI
    public static void openTpaGui(ServerPlayerEntity sender, String specificTarget, boolean isHere) {
        SimpleInventory inv = new SimpleInventory(54);
        List<ServerPlayerEntity> targets = new ArrayList<>();

        if (specificTarget != null) {
            ServerPlayerEntity t = sender.getServer().getPlayerManager().getPlayer(specificTarget);
            if (t != null) targets.add(t);
        } else {
            for (ServerPlayerEntity p : sender.getServer().getPlayerManager().getPlayerList()) {
                if (!p.equals(sender)) targets.add(p);
            }
        }

        int row = 0;
        for (ServerPlayerEntity target : targets) {
            if (row >= 6) break;
            int base = row * 9;

            // プレイヤーヘッド
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.setCustomName(Text.literal("§e" + target.getGameProfile().getName()));
            NbtCompound tag = head.getOrCreateNbt();
            tag.putString("SkullOwner", target.getGameProfile().getName());
            inv.setStack(base, head);

            // ディメンション表示
            var dimKey = target.getWorld().getRegistryKey();
            ItemStack dimItem = new ItemStack(Items.GRASS_BLOCK);
            String dimName = "§aオーバーワールド";
            if (dimKey == World.NETHER) { dimItem = new ItemStack(Items.NETHERRACK); dimName = "§cネザー"; }
            else if (dimKey == World.END) { dimItem = new ItemStack(Items.END_STONE); dimName = "§eエンド"; }
            dimItem.setCustomName(Text.literal("§f現在地: " + dimName));
            inv.setStack(base + 1, dimItem);

            // 申請ボタン (緑色のガラス)
            ItemStack btn = createItem(Items.GREEN_STAINED_GLASS_PANE, "§a" + (isHere ? "TPAHERE" : "TPA") + " 申請を送る");
            inv.setStack(base + 2, btn);

            row++;
        }

        playerOpenGui(sender, inv, 6, "§b🦭 " + (isHere ? "TPAHERE" : "TPA") + " プレイヤー選択", (slot, pEntity) -> {
            int clickedRow = slot / 9;
            int col = slot % 9;

            if (col == 2 && clickedRow < targets.size()) {
                ServerPlayerEntity t = targets.get(clickedRow);
                AzarasiManager.sendTpaRequest(pEntity, t, isHere);
                pEntity.closeHandledScreen();
            }
        });
    }

    // Home 管理 GUI
    public static void openHomeGui(ServerPlayerEntity player) {
        SimpleInventory inv = new SimpleInventory(27);
        Map<String, AzarasiManager.HomeLoc> homes = AzarasiManager.getPlayerHomes(player);

        int slot = 0;
        List<String> homeNames = new ArrayList<>();
        for (Map.Entry<String, AzarasiManager.HomeLoc> entry : homes.entrySet()) {
            if (slot >= 27) break;
            homeNames.add(entry.getKey());
            ItemStack bed = createItem(Items.RED_BED, "§a" + entry.getKey());
            NbtCompound tag = bed.getOrCreateNbt();
            bed.setCustomName(Text.literal("§a" + entry.getKey() + " §7(左クリック: TP / 右クリック: 削除)"));
            inv.setStack(slot++, bed);
        }

        playerOpenGui(player, inv, 3, "§b🦭 保存された Home 一覧", (s, pEntity) -> {
            if (s < homeNames.size()) {
                String homeName = homeNames.get(s);
                AzarasiManager.tpHome(pEntity, homeName);
                pEntity.closeHandledScreen();
            }
        });
    }

    // PvP 切り替え GUI
    public static void openPvpGui(ServerPlayerEntity player) {
        SimpleInventory inv = new SimpleInventory(27);
        boolean enabled = AzarasiManager.isPvpEnabled(player.getUuid());

        ItemStack pvpBtn = createItem(
            enabled ? Items.GREEN_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE,
            enabled ? "§aPvP: ON (クリックでOFF)" : "§cPvP: OFF (クリックでON)"
        );
        inv.setStack(13, pvpBtn);

        playerOpenGui(player, inv, 3, "§b🦭 PvP 設定", (slot, pEntity) -> {
            if (slot == 13) {
                AzarasiManager.togglePvp(pEntity.getUuid());
                pEntity.sendMessage(Text.literal("§a[AzarasiSMP] PvP設定を変更したよ！"), false);
                pEntity.closeHandledScreen();
            }
        });
    }

    private static ItemStack createItem(net.minecraft.item.Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.setCustomName(Text.literal(name));
        return stack;
    }

    private static void playerOpenGui(ServerPlayerEntity player, SimpleInventory inv, int rows, String title, java.util.function.BiConsumer<Integer, ServerPlayerEntity> onClick) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, p) -> 
            new CustomChestHandler(rows == 6 ? ScreenHandlerType.GENERIC_9X6 : ScreenHandlerType.GENERIC_9X3, syncId, playerInv, inv, rows, onClick),
            Text.literal(title)));
    }

    // アイテムの持ち出しを防ぐ標準CustomChestHandler
    private static class CustomChestHandler extends ChestScreenHandler {
        private final java.util.function.BiConsumer<Integer, ServerPlayerEntity> onClick;

        public CustomChestHandler(ScreenHandlerType<ChestScreenHandler> type, int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, net.minecraft.inventory.Inventory inventory, int rows, java.util.function.BiConsumer<Integer, ServerPlayerEntity> onClick) {
            super(type, syncId, playerInventory, inventory, rows);
            this.onClick = onClick;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, net.minecraft.entity.player.PlayerEntity player) {
            if (slotIndex >= 0 && slotIndex < getRows() * 9) {
                if (onClick != null && player instanceof ServerPlayerEntity sp) {
                    onClick.accept(slotIndex, sp);
                }
                this.sendContentUpdates();
                return;
            }
            super.onSlotClick(slotIndex, button, actionType, player);
        }
    }
}
