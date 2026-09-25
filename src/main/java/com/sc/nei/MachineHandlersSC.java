package com.sc.nei;

import com.sc.machine.MachineType;

/**
 * One NEI handler class per machine. NEI 1.0.5's registerRecipeHandler / registerUsageHandler
 * silently drop a handler whose CLASS is already registered, so 29 instances of one class
 * left only the first (the Crusher) - every other machine's recipes were missing from NEI.
 * Each subclass only fixes the machine type; NEI's default newInstance() recreates it by class.
 *
 * A new MachineType needs a line here too - NEISiliconAgeConfig logs any type left out.
 */
public final class MachineHandlersSC {

    private MachineHandlersSC() {
    }

    public static class Crusher extends MachineRecipeHandlerSC {
        public Crusher() {
            super(MachineType.CRUSHER);
        }
    }

    public static class OreWasher extends MachineRecipeHandlerSC {
        public OreWasher() {
            super(MachineType.ORE_WASHER);
        }
    }

    public static class BlastFurnace extends MachineRecipeHandlerSC {
        public BlastFurnace() {
            super(MachineType.BLAST_FURNACE);
        }
    }

    public static class ChemReactor extends MachineRecipeHandlerSC {
        public ChemReactor() {
            super(MachineType.CHEM_REACTOR);
        }
    }

    public static class CvdChamber extends MachineRecipeHandlerSC {
        public CvdChamber() {
            super(MachineType.CVD_CHAMBER);
        }
    }

    public static class CzochralskiPuller extends MachineRecipeHandlerSC {
        public CzochralskiPuller() {
            super(MachineType.CZOCHRALSKI_PULLER);
        }
    }

    public static class CzochralskiPullerEv extends MachineRecipeHandlerSC {
        public CzochralskiPullerEv() {
            super(MachineType.CZOCHRALSKI_PULLER_EV);
        }
    }

    public static class WireSaw extends MachineRecipeHandlerSC {
        public WireSaw() {
            super(MachineType.WIRE_SAW);
        }
    }

    public static class OxidationFurnace extends MachineRecipeHandlerSC {
        public OxidationFurnace() {
            super(MachineType.OXIDATION_FURNACE);
        }
    }

    public static class PhotoresistCoater extends MachineRecipeHandlerSC {
        public PhotoresistCoater() {
            super(MachineType.PHOTORESIST_COATER);
        }
    }

    public static class Stepper extends MachineRecipeHandlerSC {
        public Stepper() {
            super(MachineType.STEPPER);
        }
    }

    public static class StepperEv extends MachineRecipeHandlerSC {
        public StepperEv() {
            super(MachineType.STEPPER_EV);
        }
    }

    public static class EtchingBath extends MachineRecipeHandlerSC {
        public EtchingBath() {
            super(MachineType.ETCHING_BATH);
        }
    }

    public static class IonImplanter extends MachineRecipeHandlerSC {
        public IonImplanter() {
            super(MachineType.ION_IMPLANTER);
        }
    }

    public static class Sputterer extends MachineRecipeHandlerSC {
        public Sputterer() {
            super(MachineType.SPUTTERER);
        }
    }

    public static class DicingSaw extends MachineRecipeHandlerSC {
        public DicingSaw() {
            super(MachineType.DICING_SAW);
        }
    }

    public static class Packager extends MachineRecipeHandlerSC {
        public Packager() {
            super(MachineType.PACKAGER);
        }
    }

    public static class Centrifuge extends MachineRecipeHandlerSC {
        public Centrifuge() {
            super(MachineType.CENTRIFUGE);
        }
    }

    public static class ChlorAlkaliElectrolyzer extends MachineRecipeHandlerSC {
        public ChlorAlkaliElectrolyzer() {
            super(MachineType.CHLOR_ALKALI_ELECTROLYZER);
        }
    }

    public static class AirSeparator extends MachineRecipeHandlerSC {
        public AirSeparator() {
            super(MachineType.AIR_SEPARATOR);
        }
    }

    public static class Refinery extends MachineRecipeHandlerSC {
        public Refinery() {
            super(MachineType.REFINERY);
        }
    }

    public static class RollingMachine extends MachineRecipeHandlerSC {
        public RollingMachine() {
            super(MachineType.ROLLING_MACHINE);
        }
    }

    public static class UpgradeStationMv extends MachineRecipeHandlerSC {
        public UpgradeStationMv() {
            super(MachineType.UPGRADE_STATION_MV);
        }
    }

    public static class UpgradeStationHv extends MachineRecipeHandlerSC {
        public UpgradeStationHv() {
            super(MachineType.UPGRADE_STATION_HV);
        }
    }

    public static class UpgradeStationEv extends MachineRecipeHandlerSC {
        public UpgradeStationEv() {
            super(MachineType.UPGRADE_STATION_EV);
        }
    }

    public static class Kiln extends MachineRecipeHandlerSC {
        public Kiln() {
            super(MachineType.KILN);
        }
    }

    public static class FluidCellFiller extends MachineRecipeHandlerSC {
        public FluidCellFiller() {
            super(MachineType.FLUID_CELL_FILLER);
        }
    }

    public static class BoilerLv extends MachineRecipeHandlerSC {
        public BoilerLv() {
            super(MachineType.BOILER_LV);
        }
    }

    public static class BoilerMv extends MachineRecipeHandlerSC {
        public BoilerMv() {
            super(MachineType.BOILER_MV);
        }
    }

    static MachineRecipeHandlerSC[] all() {
        return new MachineRecipeHandlerSC[]{
                new Crusher(),
                new OreWasher(),
                new BlastFurnace(),
                new ChemReactor(),
                new CvdChamber(),
                new CzochralskiPuller(),
                new CzochralskiPullerEv(),
                new WireSaw(),
                new OxidationFurnace(),
                new PhotoresistCoater(),
                new Stepper(),
                new StepperEv(),
                new EtchingBath(),
                new IonImplanter(),
                new Sputterer(),
                new DicingSaw(),
                new Packager(),
                new Centrifuge(),
                new ChlorAlkaliElectrolyzer(),
                new AirSeparator(),
                new Refinery(),
                new RollingMachine(),
                new UpgradeStationMv(),
                new UpgradeStationHv(),
                new UpgradeStationEv(),
                new Kiln(),
                new FluidCellFiller(),
                new BoilerLv(),
                new BoilerMv()
        };
    }
}
