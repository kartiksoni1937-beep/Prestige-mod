package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.BlockVisibilityEvent;
import dev.zprestige.prestige.event.FullBrightEvent;
import java.util.Arrays;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

public class XRay extends Module {
    public XRay() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting fullBright;
    public static List xrayBlocks;

    @Override
    public void onEnable() {
        this.reloadChunks();
    }

    @Override
    public void onDisable() {
        this.reloadChunks();
    }

    @Subscribe
    public void onFullBright(FullBrightEvent event) {
        if (this.fullBright.getValue()) {
            event.cancel();
        }
    }

    @Subscribe
    public void onBlockVisibility(BlockVisibilityEvent event) {
        if (event.getBlock() != null && this.isXrayBlock(event.getBlock())) {
            event.setVisible(true);
        }

        event.cancel();
    }

    public boolean isXrayBlock(Block block) {
        return xrayBlocks.contains(block);
    }

    public void reloadChunks() {
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
            mc.worldRenderer.scheduleTerrainUpdate();
        }
    }

    static {
        long[] slotIndices = new long[]{-965180847646834672L, -4560921742666629086L, 8926569193743056950L, -2692947904796033016L, -5341203359162433522L, -8772031253911175139L, 4452274398816108577L, 8145793621648474163L, -1652677113121079279L, 3146527104435224615L, -7982608722943803344L, -8189377747177439206L, 8467311858359992366L, -7691443587048800234L, 3707995835305295902L, 2996114386201870360L, 556036769766703136L, -1130760903583596533L, -3911967465734668275L, -9165016964744085479L, -4361975451606843350L, -3399299295743049681L, 405124933383880711L, -5938611710645501933L, -8795471080878768119L, 542933309877911579L, -9135026719944605668L, 5335918290235555855L, 1628068299134730275L, -65652401935220724L, 7789352679435665460L, -392972435088998351L, -1650343343856549870L, -1390015927117938667L, -7181382681249185755L, -1773966826351362038L, 645987347883622445L, -5139079277634912225L, -374112086935994315L, -7644537617848991706L, -5167832597622947817L, -8010885589324791758L, -6404928211326599132L, 4273689366590652460L, -3635677455148122069L, 5671192315058716712L, 2588953419276353542L, -8924114486789406679L, -4278980353982463980L};
        Block[] blockArray = new Block[]{
            Blocks.EMERALD_ORE,
            Blocks.EMERALD_BLOCK,
            Blocks.DIAMOND_ORE,
            Blocks.DIAMOND_BLOCK,
            Blocks.GOLD_ORE,
            Blocks.GOLD_BLOCK,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        };
        blockArray[(int)slotIndices[46]] = Blocks.IRON_ORE;
        blockArray[(int)slotIndices[22]] = Blocks.IRON_BLOCK;
        blockArray[(int)slotIndices[3]] = Blocks.COAL_ORE;
        blockArray[(int)slotIndices[24]] = Blocks.COAL_BLOCK;
        blockArray[(int)slotIndices[35]] = Blocks.REDSTONE_ORE;
        blockArray[(int)slotIndices[17]] = Blocks.REDSTONE_BLOCK;
        blockArray[(int)slotIndices[29]] = Blocks.LAPIS_ORE;
        blockArray[(int)slotIndices[18]] = Blocks.LAPIS_BLOCK;
        blockArray[(int)slotIndices[4]] = Blocks.NETHER_QUARTZ_ORE;
        blockArray[(int)slotIndices[27]] = Blocks.NETHER_GOLD_ORE;
        blockArray[(int)slotIndices[0]] = Blocks.DEEPSLATE_EMERALD_ORE;
        blockArray[(int)slotIndices[8]] = Blocks.DEEPSLATE_DIAMOND_ORE;
        blockArray[(int)slotIndices[32]] = Blocks.DEEPSLATE_GOLD_ORE;
        blockArray[(int)slotIndices[23]] = Blocks.DEEPSLATE_IRON_ORE;
        blockArray[(int)slotIndices[48]] = Blocks.DEEPSLATE_COAL_ORE;
        blockArray[(int)slotIndices[33]] = Blocks.ANCIENT_DEBRIS;
        blockArray[(int)slotIndices[13]] = Blocks.WATER;
        blockArray[(int)slotIndices[40]] = Blocks.LAVA;
        blockArray[(int)slotIndices[15]] = Blocks.OBSIDIAN;
        blockArray[(int)slotIndices[19]] = Blocks.CRYING_OBSIDIAN;
        blockArray[(int)slotIndices[11]] = Blocks.BEACON;
        blockArray[(int)slotIndices[25]] = Blocks.ENDER_CHEST;
        blockArray[(int)slotIndices[26]] = Blocks.CHEST;
        blockArray[(int)slotIndices[5]] = Blocks.TRAPPED_CHEST;
        blockArray[(int)slotIndices[14]] = Blocks.BARREL;
        blockArray[(int)slotIndices[37]] = Blocks.HOPPER;
        blockArray[(int)slotIndices[16]] = Blocks.FURNACE;
        blockArray[(int)slotIndices[6]] = Blocks.BLAST_FURNACE;
        blockArray[(int)slotIndices[1]] = Blocks.SMOKER;
        blockArray[(int)slotIndices[28]] = Blocks.ANVIL;
        blockArray[(int)slotIndices[42]] = Blocks.ENCHANTING_TABLE;
        blockArray[(int)slotIndices[34]] = Blocks.BREWING_STAND;
        blockArray[(int)slotIndices[39]] = Blocks.DISPENSER;
        blockArray[(int)slotIndices[9]] = Blocks.DROPPER;
        blockArray[(int)slotIndices[45]] = Blocks.GLOWSTONE;
        blockArray[(int)slotIndices[47]] = Blocks.SEA_LANTERN;
        blockArray[(int)slotIndices[20]] = Blocks.TORCH;
        blockArray[(int)slotIndices[44]] = Blocks.SOUL_TORCH;
        blockArray[(int)slotIndices[43]] = Blocks.CAMPFIRE;
        blockArray[(int)slotIndices[36]] = Blocks.SOUL_CAMPFIRE;
        blockArray[(int)slotIndices[12]] = Blocks.SPAWNER;
        blockArray[(int)slotIndices[21]] = Blocks.END_PORTAL_FRAME;
        blockArray[(int)slotIndices[10]] = Blocks.END_GATEWAY;
        blockArray[(int)slotIndices[31]] = Blocks.NETHER_PORTAL;
        blockArray[(int)slotIndices[41]] = Blocks.MOSSY_COBBLESTONE;
        blockArray[(int)slotIndices[7]] = Blocks.STONE_BRICKS;
        blockArray[(int)slotIndices[30]] = Blocks.OAK_PLANKS;
        blockArray[(int)slotIndices[38]] = Blocks.NOTE_BLOCK;
        xrayBlocks = Arrays.asList(blockArray);
    }
}
