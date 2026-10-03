package com.sc.init;

import net.minecraft.block.Block;
import com.sc.block.BlockCableSC;
import com.sc.block.BlockFieldGeneratorSC;
import com.sc.block.BlockGeneratorSC;
import com.sc.block.BlockItemTubeSC;
import com.sc.block.BlockLimestoneSC;
import com.sc.block.BlockMachineSC;
import com.sc.block.BlockOreSC;
import com.sc.block.BlockPipeSC;
import com.sc.block.ItemBlockCableSC;
import com.sc.block.ItemBlockGeneratorSC;
import com.sc.block.ItemBlockMachineSC;
import com.sc.block.ItemBlockOreSC;
import com.sc.block.ItemBlockPipeSC;
import com.sc.energy.CableType;
import com.sc.tileentity.TileEntityCableSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.tileentity.TileEntityGeneratorSC;
import com.sc.tileentity.TileEntityItemTubeSC;
import com.sc.tileentity.TileEntityMachineSC;
import com.sc.tileentity.TileEntityPipeSC;
import com.sc.util.OreEntry;
import com.sc.util.PipeType;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

public final class ModBlocks {

    public static BlockOreSC oreSC;
    public static BlockLimestoneSC limestoneSC;
    public static BlockCableSC cableSC;
    public static BlockPipeSC pipeSC;
    public static BlockItemTubeSC tubeItemPneumatic;
    public static com.sc.block.BlockConduitSC conduitBundle;
    public static BlockMachineSC machineSC;
    public static BlockMachineSC machineSC2;
    public static BlockGeneratorSC generatorSC;
    public static BlockGeneratorSC generatorSC2;
    public static Block tokamakCoil;
    /** The Singular Reactor's gravity coils (16 in its 7x7x5 build). */
    public static Block gravityCoil;
    /** Radiation: the lead block and lead glass (shielding), the decontamination shower. */
    public static com.sc.block.BlockLeadSC.Solid leadBlock;
    public static com.sc.block.BlockLeadSC.Glass leadGlass;
    public static com.sc.block.BlockShowerSC shower;
    /** Blocks of the mod's metals (BlockMetalSC.METALS over two blocks). */
    public static com.sc.block.BlockMetalSC metalBlock, metalBlock2;
    public static com.sc.block.BlockQuarrySC quarrySC;

    /** The item of a generator type (the block and metadata it lives on). */
    public static net.minecraft.item.ItemStack generatorStack(com.sc.energy.GeneratorType type, int count) {
        BlockGeneratorSC block = type.ordinal() < 16 ? generatorSC : generatorSC2;
        return new net.minecraft.item.ItemStack(block, count, type.ordinal() - block.getTypeOffset());
    }

    /** The generator type of a generator item, or null. */
    public static com.sc.energy.GeneratorType generatorTypeOf(net.minecraft.item.ItemStack stack) {
        if (stack == null) {
            return null;
        }
        Block b = Block.getBlockFromItem(stack.getItem());
        return b instanceof BlockGeneratorSC ? ((BlockGeneratorSC) b).typeFor(stack.getItemDamage()) : null;
    }
    public static BlockFieldGeneratorSC fieldGeneratorSC;
    public static com.sc.block.BlockEnergyStorageSC energyStorageSC;
    /** Wireless energy: transmitter / receiver LV..XV, the quantum translator (XV). */
    public static com.sc.block.BlockWirelessSC wirelessTx, wirelessRx, quantumTranslator;
    public static com.sc.block.BlockTransformerSC transformerSC;
    public static com.sc.block.BlockTankSC tankSC;
    public static com.sc.block.BlockChargePadSC chargePadSC;
    /** The Armour Service Station (MV): gases and EU for the energy suits. */
    public static com.sc.block.BlockArmorStationSC armorStation;
    /** The Singular Service Station (SV) and its Gravitational Stabiliser. */
    public static com.sc.block.BlockSingularStationSC singularStation;
    public static com.sc.block.BlockGravStabiliserSC gravStabiliser;
    /** The krypton searchlight's moving light (ArmorLogicSC.searchlight). */
    public static com.sc.block.BlockLightSC lightSC;

    private ModBlocks() {
    }

    public static void init() {
        oreSC = new BlockOreSC();
        GameRegistry.registerBlock(oreSC, ItemBlockOreSC.class, "oreSC");

        limestoneSC = new BlockLimestoneSC();
        GameRegistry.registerBlock(limestoneSC, com.sc.block.ItemBlockLimestoneSC.class, "limestoneSC");

        cableSC = new BlockCableSC();
        GameRegistry.registerBlock(cableSC, ItemBlockCableSC.class, "cableSC");

        pipeSC = new BlockPipeSC();
        GameRegistry.registerBlock(pipeSC, ItemBlockPipeSC.class, "pipeSC");

        tubeItemPneumatic = new BlockItemTubeSC();
        GameRegistry.registerBlock(tubeItemPneumatic, com.sc.block.ItemBlockTubeSC.class, "tubeItemPneumatic");

        // Ender IO style conduit bundle: where the cable / pipe / tube items actually go.
        conduitBundle = new com.sc.block.BlockConduitSC();
        GameRegistry.registerBlock(conduitBundle, "conduitBundle");

        // 29 MachineType values > 16 metadata slots (same wall as BlockOreSC hit for 16 ores,
        // §10) - split across two blocks, ordinals 0-15 and 16-28.
        machineSC = new BlockMachineSC(0);
        GameRegistry.registerBlock(machineSC, ItemBlockMachineSC.class, "machineSC");
        machineSC2 = new BlockMachineSC(16);
        GameRegistry.registerBlock(machineSC2, ItemBlockMachineSC.class, "machineSC2");

        generatorSC = new BlockGeneratorSC(0);
        GameRegistry.registerBlock(generatorSC, ItemBlockGeneratorSC.class, "generatorSC");
        // more generators than 16 metadata values: the rest on a second block (like machines)
        generatorSC2 = new BlockGeneratorSC(16);
        GameRegistry.registerBlock(generatorSC2, ItemBlockGeneratorSC.class, "generatorSC2");
        quarrySC = new com.sc.block.BlockQuarrySC();
        GameRegistry.registerBlock(quarrySC, com.sc.block.ItemBlockQuarrySC.class, "quarrySC");
        tokamakCoil = new com.sc.block.BlockTokamakCoilSC();
        GameRegistry.registerBlock(tokamakCoil, com.sc.block.ItemBlockTokamakCoilSC.class, "tokamakCoil");
        gravityCoil = new com.sc.block.BlockGravityCoilSC();
        GameRegistry.registerBlock(gravityCoil, com.sc.block.ItemBlockGravityCoilSC.class, "gravityCoil");
        leadBlock = new com.sc.block.BlockLeadSC.Solid();
        GameRegistry.registerBlock(leadBlock, com.sc.block.ItemBlockLeadSC.class, "leadBlock");
        net.minecraftforge.oredict.OreDictionary.registerOre("blockLead", leadBlock);
        leadGlass = new com.sc.block.BlockLeadSC.Glass();
        GameRegistry.registerBlock(leadGlass, com.sc.block.ItemBlockLeadSC.class, "leadGlass");
        metalBlock = new com.sc.block.BlockMetalSC(0);
        GameRegistry.registerBlock(metalBlock, com.sc.block.BlockMetalSC.ItemMetalBlock.class, "metalBlock");
        metalBlock2 = new com.sc.block.BlockMetalSC(16);
        GameRegistry.registerBlock(metalBlock2, com.sc.block.BlockMetalSC.ItemMetalBlock.class, "metalBlock2");
        for (com.sc.util.Material m : com.sc.block.BlockMetalSC.METALS) {
            net.minecraftforge.oredict.OreDictionary.registerOre("block" + m.oreDictName, com.sc.block.BlockMetalSC.stackOf(m, 1));
            if (m == com.sc.util.Material.ALUMINIUM) {
                net.minecraftforge.oredict.OreDictionary.registerOre("blockAluminum", com.sc.block.BlockMetalSC.stackOf(m, 1));
            }
        }
        shower = new com.sc.block.BlockShowerSC();
        GameRegistry.registerBlock(shower, com.sc.block.ItemBlockShowerSC.class, "shower");
        GameRegistry.registerTileEntity(com.sc.tileentity.TileEntityShowerSC.class, "SiliconAge.shower");

        fieldGeneratorSC = new BlockFieldGeneratorSC();
        GameRegistry.registerBlock(fieldGeneratorSC, com.sc.block.ItemBlockFieldGeneratorSC.class, "fieldGeneratorSC");

        energyStorageSC = new com.sc.block.BlockEnergyStorageSC();
        GameRegistry.registerBlock(energyStorageSC, com.sc.block.ItemBlockEnergyStorageSC.class, "energyStorageSC");
        transformerSC = new com.sc.block.BlockTransformerSC();
        GameRegistry.registerBlock(transformerSC, com.sc.block.ItemBlockTransformerSC.class, "transformerSC");
        tankSC = new com.sc.block.BlockTankSC();
        GameRegistry.registerBlock(tankSC, com.sc.block.ItemBlockTankSC.class, "tankSC");
        // IC2-style charge pads: an energy storage with a pad on top
        chargePadSC = new com.sc.block.BlockChargePadSC();
        GameRegistry.registerBlock(chargePadSC, com.sc.block.ItemBlockEnergyStorageSC.class, "chargePadSC");
        armorStation = new com.sc.block.BlockArmorStationSC();
        GameRegistry.registerBlock(armorStation, com.sc.block.ItemBlockArmorStationSC.class, "armorStation");
        GameRegistry.registerTileEntity(com.sc.tileentity.TileEntityArmorStationSC.class, "SiliconAge.armorStation");
        // the helmet's krypton searchlight: an invisible light that goes out by itself (no item in any tab)
        lightSC = new com.sc.block.BlockLightSC();
        GameRegistry.registerBlock(lightSC, "lightSC");
        singularStation = new com.sc.block.BlockSingularStationSC();
        GameRegistry.registerBlock(singularStation, com.sc.block.ItemBlockSingularStationSC.class, "singularStation");
        GameRegistry.registerTileEntity(com.sc.tileentity.TileEntitySingularStationSC.class, "SiliconAge.singularStation");
        gravStabiliser = new com.sc.block.BlockGravStabiliserSC();
        GameRegistry.registerBlock(gravStabiliser, com.sc.block.ItemBlockGravStabiliserSC.class, "gravStabiliser");
        GameRegistry.registerTileEntity(com.sc.tileentity.TileEntityGravStabiliserSC.class, "SiliconAge.gravStabiliser");   // with its item: a world that knew the item refuses to load without it ("dangling"); hidden from creative

        GameRegistry.registerTileEntity(TileEntityCableSC.class, "SiliconAge.cable");
        GameRegistry.registerTileEntity(TileEntityPipeSC.class, "SiliconAge.pipe");
        GameRegistry.registerTileEntity(TileEntityItemTubeSC.class, "SiliconAge.itemTube");
        GameRegistry.registerTileEntity(com.sc.tileentity.TileEntityConduitBundleSC.class, "SiliconAge.conduitBundle");
        GameRegistry.registerTileEntity(TileEntityMachineSC.class, "SiliconAge.machine");
        GameRegistry.registerTileEntity(TileEntityGeneratorSC.class, "SiliconAge.generator");
        GameRegistry.registerTileEntity(TileEntityFieldGeneratorSC.class, "SiliconAge.fieldGenerator");
        wirelessTx = new com.sc.block.BlockWirelessSC(com.sc.tileentity.TileEntityWirelessSC.TRANSMITTER);
        GameRegistry.registerBlock(wirelessTx, com.sc.block.ItemBlockWirelessSC.class, "wirelessTx");
        wirelessRx = new com.sc.block.BlockWirelessSC(com.sc.tileentity.TileEntityWirelessSC.RECEIVER);
        GameRegistry.registerBlock(wirelessRx, com.sc.block.ItemBlockWirelessSC.class, "wirelessRx");
        quantumTranslator = new com.sc.block.BlockWirelessSC(com.sc.tileentity.TileEntityWirelessSC.QUANTUM);
        GameRegistry.registerBlock(quantumTranslator, com.sc.block.ItemBlockWirelessSC.class, "quantumTranslator");
        GameRegistry.registerTileEntity(com.sc.tileentity.TileEntityWirelessSC.class, "SiliconAge.wireless");
        GameRegistry.registerTileEntity(com.sc.tileentity.TileEntityEnergyStorageSC.class, "SiliconAge.energyStorage");
        GameRegistry.registerTileEntity(com.sc.tileentity.TileEntityTransformerSC.class, "SiliconAge.transformer");
        GameRegistry.registerTileEntity(com.sc.tileentity.TileEntityTankSC.class, "SiliconAge.tank");
        GameRegistry.registerTileEntity(com.sc.tileentity.TileEntityChargePadSC.class, "SiliconAge.chargePad");
        GameRegistry.registerTileEntity(com.sc.tileentity.TileEntityQuarrySC.class, "SiliconAge.quarry");

        registerOreDict();
    }

    // Step 3 (§11): OreDict entries for the raw ore blocks themselves ("oreCopper" etc.),
    // independent of whether that ore also has a generic crushedOre/dust/ingot chain -
    // Halite/Magnesite/Quartzite are chain-exceptions (§11.2) but are still ores, so they're
    // registered here too.
    // Step 4 (§12.5): wireX/pipeX/tubeItemPneumatic OreDict entries for cables and pipes.
    private static void registerOreDict() {
        for (OreEntry ore : OreEntry.values()) {
            ItemStack stack = new ItemStack(oreSC, 1, ore.meta());
            OreDictionary.registerOre("ore" + capitalize(ore.oreName), stack);
        }
        for (CableType type : CableType.values()) {
            OreDictionary.registerOre(type.oreDictName, new ItemStack(cableSC, 1, type.ordinal()));
        }
        for (PipeType type : PipeType.values()) {
            OreDictionary.registerOre(type.textureName, new ItemStack(pipeSC, 1, type.ordinal()));
        }
        OreDictionary.registerOre("tubeItemPneumatic", new ItemStack(tubeItemPneumatic));
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
