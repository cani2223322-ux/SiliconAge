package com.sc.util;

/**
 * The solid intermediates of the silicon chain (§3) plus the GaAs subsystem (§18.1) -
 * deliberately separate from Material/ItemMaterialSC (§11.2: "Silicon исключён из этой схемы,
 * у него отдельная цепочка"). One metadata value per stage, in processing order.
 */
public enum SiliconMaterial {

    SILICA_SAND("silicaSand"),
    METALLURGICAL_SI("metallurgicalSi"),
    ELECTRONIC_GRADE_SI("electronicGradeSi"),
    SI_INGOT("siIngot"),
    SI_WAFER("siWafer"),
    OXIDIZED_WAFER("oxidizedWafer"),
    COATED_WAFER("coatedWafer"),
    EXPOSED_WAFER("exposedWafer"),
    ETCHED_WAFER("etchedWafer"),
    DOPED_WAFER("dopedWafer"),
    ANNEALED_WAFER("annealedWafer"),
    METALLIZED_WAFER("metallizedWafer"),
    DIE("die"),
    TRANSISTOR("transistor"),
    MEMORY_CHIP("memoryChip"),
    CONTROLLER("controller"),
    // §18.1 GaAs subsystem - reuses Chem Reactor/Czochralski Puller/Wire Saw, not new machines.
    GAAS_INGOT("gaAsIngot"),
    GAAS_WAFER("gaAsWafer"),
    // ОМ-4 (docs/plan-ideas-2026-10-26.md, «ЭУФ-литограф и процессоры», the coordinator's - appended, metas 18..21): the EUV
    // Lithograph's wafer, the Processor (QV), the Exo Processor (XV), the Singular Matrix (SV)
    PROCESSOR_WAFER("processorWafer"),
    PROCESSOR("processor"),
    EXO_PROCESSOR("exoProcessor"),
    SINGULAR_MATRIX("singularMatrix");

    public final String textureName;

    SiliconMaterial(String textureName) {
        this.textureName = textureName;
    }
}
