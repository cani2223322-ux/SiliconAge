package com.sc.embassy;

import com.sc.block.BlockShowcase2SC;
import com.sc.block.BlockShowcaseSC;
import com.sc.dim.SubstrateBlocksSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.tileentity.TileEntityShowcaseSC;
import com.sc.util.Material;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

/**
 * PACKAGE S (docs/plan-substrate-tech-f6.md §6.2, rule ПЕ-7): one workshop of an embassy's ring - "SCOwEmbWorkshop", 11 x 11
 * (EmbassyLayoutSC.WORKSHOP), one a trade of the roster (Lot.variant = its NpcKindSC ordinal). A City's workshop of the Substrate
 * (settle.gen.ComponentSetWorkshopSC - the same walls, roofs and machines: SettlementLayoutSC.workshopSpots / workshopLine, the
 * Tuners' posts by workshopPosts), built so that it is idempotent - the «Достройка» runs it again: a block set into the floor (the
 * clean room's shower at the door, the Power Engineer's charge pads) is never laid as floor first in the same pass.
 * <ul>
 * <li>Technologist - «чистая комната»: lead glass walls on titanium, a quartz floor; the five machines; the shower at the door;</li>
 * <li>Metallurgist - brick on steel; three machines; the trim's tantalum, niobium, hafnium and tungsten stacked on the free spots;</li>
 * <li>Gas Technician - aluminium on titanium; the separator, the centrifuge, three tanks;</li>
 * <li>Cartographer - banded oxide crust on niobium; the Focuser in the middle, lead glass map cases along the back;</li>
 * <li>Power Engineer - brick on zirconium, a copper trace from pad to pad; the transformer between two storages, two pads set in the
 * floor, spare coils; the solar panels and the wind generator on the steel roof;</li>
 * <li>Salvager - «Склад»: steel on tungsten, a loading gate five wide; the scrap heaped between racks, crates two high.</li>
 * </ul>
 * All the metal, the coils and the lead glass are the trim (unbreakable, nothing to carry away); not one log or plank.
 */
public class ComponentEmbWorkshopSC extends ComponentEmbassySC {

    public ComponentEmbWorkshopSC() {
    }

    public ComponentEmbWorkshopSC(int id, EmbassyLayoutSC.Plan p, SettlementLayoutSC.Lot lot) {
        super(id, p.floorY, lot, -1, SettlementLayoutSC.workshopHeight(NpcKindSC.of(lot.variant)));
    }

    @Override
    protected void build(World w, StructureBoundingBox win) {
        NpcKindSC kind = NpcKindSC.of(variant);
        int n = width(), c = n / 2;
        boolean tech = kind == NpcKindSC.TECHNOLOGIST, power = kind == NpcKindSC.POWER_ENGINEER, store = kind == NpcKindSC.SALVAGER;
        int[][] spots = SettlementLayoutSC.workshopSpots(n);
        int[] machines = SettlementLayoutSC.workshopLine(kind);
        ItemStack post, ring, roof, wallMetal = null;
        Block wall = rock();
        int wallMeta = BRICK;
        switch (kind) {
            case TECHNOLOGIST:
                post = ring = roof = titanium();
                wall = leadGlass();
                wallMeta = 0;
                break;
            case GAS_TECH:
                post = ring = titanium();
                roof = wallMetal = metal(Material.ALUMINIUM);
                break;
            case CARTOGRAPHER:
                post = ring = metal(Material.NIOBIUM);
                roof = null;                                           // a brick roof
                break;
            case POWER_ENGINEER:
                post = ring = metal(Material.ZIRCONIUM);
                roof = steel();
                break;
            case SALVAGER:
                post = ring = metal(Material.TUNGSTEN);
                roof = wallMetal = steel();
                break;
            default:
                post = ring = roof = steel();
        }
        int doorHalf = store ? 2 : 1;                                  // the warehouse's loading gate
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                boolean edgeU = u == 0 || u == n - 1, edgeV = v == 0 || v == n - 1;
                // the floor: quartz in the clean room, brick elsewhere (and under every wall); the Power Engineer's copper trace -
                // never under a block set into the floor (the shower, the pads: they are laid below, once)
                if (inFloor(kind, spots, machines, c, u, v)) {
                    // its own block goes there
                } else if (tech && !edgeU && !edgeV) {
                    set(w, win, u, 0, v, Blocks.quartz_block, 0);
                } else if (power && !edgeU && !edgeV && (v == n - 4 || u == c && v >= n - 4)) {
                    set(w, win, u, 0, v, rock(), SubstrateBlocksSC.COPPER_TRACE);
                } else {
                    set(w, win, u, 0, v, rock(), BRICK);
                }
                // the roof: a skylight in the middle, four lamps
                boolean sky = Math.abs(u - c) <= 1 && Math.abs(v - c) <= 1;
                boolean lamp = (u == 2 || u == n - 3) && (v == 2 || v == n - 3);
                if (sky) {
                    set(w, win, u, 5, v, leadGlass(), 0);
                } else if (lamp) {
                    set(w, win, u, 5, v, rock(), SubstrateBlocksSC.TRACE_NODE);
                } else if (roof != null) {
                    set(w, win, u, 5, v, roof);
                } else {
                    set(w, win, u, 5, v, rock(), BRICK);
                }
                if (!edgeU && !edgeV) {
                    continue;
                }
                // the walls
                boolean corner = edgeU && edgeV, door = v == 0 && Math.abs(u - c) <= doorHalf;
                for (int dy = 1; dy <= 4; dy++) {
                    if (corner) {
                        set(w, win, u, dy, v, post);
                    } else if (door && dy <= 3) {
                        clear(w, win, u, dy, v);
                    } else if (dy == 4) {
                        if (door && u == c) {
                            set(w, win, u, dy, v, rock(), SubstrateBlocksSC.TRACE_NODE);
                        } else {
                            set(w, win, u, dy, v, ring);
                        }
                    } else if (!tech && (dy == 2 || dy == 3) && v != 0 && (edgeU ? v : u) % 2 == 0 && (edgeU ? v : u) >= 2
                            && (edgeU ? v : u) <= n - 3) {
                        set(w, win, u, dy, v, leadGlass(), 0);             // the windows
                    } else if (wallMetal != null) {
                        set(w, win, u, dy, v, wallMetal);
                    } else if (kind == NpcKindSC.CARTOGRAPHER) {
                        set(w, win, u, dy, v, wall, dy == 3 ? SubstrateBlocksSC.OXIDE_VIOLET : SubstrateBlocksSC.OXIDE_BLUE);   // banded crust
                    } else {
                        set(w, win, u, dy, v, wall, wallMeta);
                    }
                }
            }
        }
        // the machines
        if (kind == NpcKindSC.CARTOGRAPHER) {
            showcase(w, win, c, 1, c, BlockShowcaseSC.FOCUSER, 0, -1);
            for (int i = 0; i < 3 && i < spots.length; i++) {          // the map cases along the back
                set(w, win, spots[i][0], 1, spots[i][1], leadGlass(), 0);
            }
            return;
        }
        ItemStack[] stock = {metal(Material.TANTALUM), metal(Material.NIOBIUM), metal(Material.HAFNIUM), metal(Material.TUNGSTEN)};
        for (int i = 0; i < spots.length; i++) {
            int[] s = spots[i];
            if (i < machines.length) {
                machine(w, win, s, machines[i], store);
            } else if (kind == NpcKindSC.METALLURGIST) {
                set(w, win, s[0], 1, s[1], stock[i % stock.length]);      // the stock of metal
            } else if (power) {
                set(w, win, s[0], 1, s[1], coil());                        // spare coils
            } else if (store) {
                machine(w, win, s, SettlementLayoutSC.SHOWCASE2 + BlockShowcase2SC.CRATE, true);
            }
        }
        if (tech) {
            showcase(w, win, c, 0, 0, BlockShowcaseSC.SHOWER, 0, 0);    // decontamination at the door
        }
        if (store) {                                                   // the scrap heaped round the pile
            int back = spots[0][1];
            showcase2(w, win, c, 2, back, BlockShowcase2SC.SCRAP_PILE, 0, -1);
            showcase2(w, win, c - 1, 1, back, BlockShowcase2SC.SCRAP_PILE, 0, -1);
            showcase2(w, win, c + 1, 1, back, BlockShowcase2SC.SCRAP_PILE, 0, -1);
        }
        if (power) {                                                   // the roof: solar panels along the back, the wind generator
            for (int u = 1; u <= n - 2; u++) {
                showcase2(w, win, u, 6, n - 2, BlockShowcase2SC.SOLAR_PANEL, 0, -1);
            }
            showcase2(w, win, n - 2, 6, 1, BlockShowcase2SC.WIND_GENERATOR, 0, -1);
        }
    }

    /** (u, v)'s floor block is a machine set into the floor: the clean room's shower at the door, a charge pad of the line. */
    private static boolean inFloor(NpcKindSC kind, int[][] spots, int[] machines, int c, int u, int v) {
        if (kind == NpcKindSC.TECHNOLOGIST && u == c && v == 0) {
            return true;
        }
        for (int i = 0; i < spots.length && i < machines.length; i++) {
            if (machines[i] == BlockShowcaseSC.CHARGE_PAD && spots[i][0] == u && spots[i][1] == v) {
                return true;
            }
        }
        return false;
    }

    /**
     * One machine of the line at spot `s` ({u, v, du, dv}): a code below SHOWCASE2 is BlockShowcaseSC's (a charge pad is set in the
     * floor), from it on BlockShowcase2SC's; `stack` - the warehouse's racks and crates two high.
     */
    private void machine(World w, StructureBoundingBox win, int[] s, int code, boolean stack) {
        if (code < SettlementLayoutSC.SHOWCASE2) {
            showcase(w, win, s[0], code == BlockShowcaseSC.CHARGE_PAD ? 0 : 1, s[1], code, s[2], s[3]);
            return;
        }
        int meta = code - SettlementLayoutSC.SHOWCASE2;
        showcase2(w, win, s[0], 1, s[1], meta, s[2], s[3]);
        if (stack && (meta == BlockShowcase2SC.RACK || meta == BlockShowcase2SC.CRATE)) {
            showcase2(w, win, s[0], 2, s[1], meta, s[2], s[3]);
        }
    }

    /** A BlockShowcase2SC machine of `meta` at (u, floor + dy, v), its front towards (du, dv) of the lot's frame. */
    private void showcase2(World w, StructureBoundingBox win, int u, int dy, int v, int meta, int du, int dv) {
        if (SettlementBlocksSC.showcase2 == null || !set(w, win, u, dy, v, SettlementBlocksSC.showcase2, meta)) {
            return;
        }
        int d = SettlementLayoutSC.frameDir(facing, du, dv);
        TileEntity te = w.getTileEntity(wx(u, v), floorY + dy, wz(u, v));
        if (d >= 0 && te instanceof TileEntityShowcaseSC) {
            ((TileEntityShowcaseSC) te).setFacing(SIDE_OF_DIR[d]);
        }
    }
}
