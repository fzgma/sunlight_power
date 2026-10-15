package com.furina23.sunlightpower.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SolarGenerationTest {
    // README rules: clear weather is unscaled; thunder replaces rain rather than stacking.
    // Different configured multipliers are essential: the shipped defaults are both 0.5.
    private static final double RAIN = 0.6;
    private static final double THUNDER = 0.25;

    @Test
    void nightAndBlockedSkyProduceNoEnergy() {
        assertEquals(0, SolarGeneration.calculate(18000, true, false, false, 8, 0.5, 0.5));
        assertEquals(0, SolarGeneration.calculate(6000, false, false, false, 8, 0.5, 0.5));
    }

    @Test
    void clearNoonIgnoresWeatherMultipliers() {
        assertEquals(100, SolarGeneration.calculate(6000, true, false, false, 100, RAIN, THUNDER));
    }

    @Test
    void rainUsesOnlyTheRainMultiplier() {
        assertEquals(60, SolarGeneration.calculate(6000, true, true, false, 100, RAIN, THUNDER));
    }

    @Test
    void thunderTakesPriorityOverRainWithoutStackingMultipliers() {
        assertEquals(25, SolarGeneration.calculate(6000, true, true, true, 100, RAIN, THUNDER));
    }

    @Test
    void thunderAlsoAppliesWhenTheRainFlagIsFalse() {
        assertEquals(25, SolarGeneration.calculate(6000, true, false, true, 100, RAIN, THUNDER));
    }

    @Test
    void weatherScalesTheDaylightCurveAndRoundsDown() {
        // At tick 3000, daylight is sin(pi / 4): approximately 0.7071.
        assertEquals(70, SolarGeneration.calculate(3000, true, false, false, 100, RAIN, THUNDER));
        assertEquals(42, SolarGeneration.calculate(3000, true, true, false, 100, RAIN, THUNDER));
        assertEquals(17, SolarGeneration.calculate(3000, true, true, true, 100, RAIN, THUNDER));
        assertEquals(11, SolarGeneration.calculate(6000, true, true, false, 19, RAIN, THUNDER));
        assertEquals(4, SolarGeneration.calculate(6000, true, true, true, 19, RAIN, THUNDER));
    }

    @Test
    void weatherMultipliersCannotIncreaseOrReverseGeneration() {
        assertEquals(100, SolarGeneration.calculate(6000, true, true, false, 100, 2, THUNDER));
        assertEquals(100, SolarGeneration.calculate(6000, true, true, true, 100, RAIN, 2));
        assertEquals(0, SolarGeneration.calculate(6000, true, true, false, 100, -1, THUNDER));
        assertEquals(0, SolarGeneration.calculate(6000, true, true, true, 100, RAIN, -1));
    }

    @Test
    void sunriseSunsetAndDisabledGenerationProduceNoEnergy() {
        assertEquals(0, SolarGeneration.calculate(0, true, false, false, 100, RAIN, THUNDER));
        assertEquals(0, SolarGeneration.calculate(12000, true, false, false, 100, RAIN, THUNDER));
        assertEquals(0, SolarGeneration.calculate(6000, true, false, false, 0, RAIN, THUNDER));
    }

    @Test
    void timeWrapsAcrossDays() {
        assertEquals(SolarGeneration.calculate(6000, true, false, false, 8, 0.5, 0.5),
                SolarGeneration.calculate(30000, true, false, false, 8, 0.5, 0.5));
        assertEquals(8, SolarGeneration.calculate(-18000, true, false, false, 8, RAIN, THUNDER));
    }
}
