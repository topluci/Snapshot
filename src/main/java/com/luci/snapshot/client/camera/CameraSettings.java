package com.luci.snapshot.client.camera;

import com.luci.snapshot.config.SnapshotConfig;
import java.util.Locale;

public final class CameraSettings {
    private static final int[] ISO_VALUES = {50, 100, 200, 400, 800, 1600, 3200, 6400, 12800};
    private static final String[] SHUTTER_LABELS = {
        "1/8000", "1/4000", "1/2000", "1/1000", "1/500", "1/250", "1/125",
        "1/60", "1/30", "1/15", "1/8", "1/4", "1/2", "1s", "2s", "4s",
        "8s", "15s", "30s", "BULB"
    };
    private static final double[] SHUTTER_SECONDS = {
        1.0 / 8000.0, 1.0 / 4000.0, 1.0 / 2000.0, 1.0 / 1000.0, 1.0 / 500.0,
        1.0 / 250.0, 1.0 / 125.0, 1.0 / 60.0, 1.0 / 30.0, 1.0 / 15.0,
        1.0 / 8.0, 1.0 / 4.0, 1.0 / 2.0, 1.0, 2.0, 4.0, 8.0, 15.0, 30.0, 120.0
    };
    private static final double[] APERTURE_VALUES = {1.2, 1.4, 1.8, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0};
    private static final int[] WB_VALUES = {2500, 2800, 3200, 3800, 4300, 4800, 5200, 5600, 6500, 7500, 9000, 10000};
    private static final String[] MIN_SHUTTER_LABELS = {"1/15", "1/30", "1/60", "1/125", "1/250"};
    private static final double[] MIN_SHUTTER_SECONDS = {1.0 / 15.0, 1.0 / 30.0, 1.0 / 60.0, 1.0 / 125.0, 1.0 / 250.0};
    private static final double ASTRO_REFERENCE_STOPS = log2(1600.0 / 100.0)
        + log2(2.0 / (1.0 / 125.0));

    private CameraControl selected = CameraControl.EXPOSURE_MODE;
    private int isoIndex = 1;
    private int shutterIndex = 6;
    private int apertureIndex = 2;
    private double focalLength = 50.0;
    private double focusDistance = 18.0;
    private int exposureCompensation = 0;
    private int whiteBalanceIndex = 8;
    private int contrast = 0;
    private int saturation = 0;
    private boolean autoFocus = true;
    private boolean flash;
    private boolean burst;
    private boolean astrophotography;
    private FilmProfile filmProfile = FilmProfile.NEUTRAL;
    private AspectRatio aspectRatio = AspectRatio.NATIVE;
    private MoodPreset mood = MoodPreset.NATURAL;
    private double rollDegrees;
    private int focusPointIndex = 4;
    private ExposureMode exposureMode = ExposureMode.MANUAL;
    private MeteringMode meteringMode = MeteringMode.EVALUATIVE;
    private boolean autoIso = true;
    private int autoIsoMaximumIndex = 5;
    private int minimumShutterIndex = 2;
    private ExposureBracket exposureBracket = ExposureBracket.OFF;
    private CaptureTechnique captureTechnique = CaptureTechnique.SINGLE;
    private ExposureAssist exposureAssist = ExposureAssist.HISTOGRAM;
    private LensProfile lens = LensProfile.KIT_ZOOM;
    private OpticalFilter filter = OpticalFilter.NONE;
    private PrintSize printSize = PrintSize.ONE_BY_ONE;
    private AstroTracking astroTracking = AstroTracking.OFF;
    private AstroStackMode astroStackMode = AstroStackMode.DEEP_SKY;
    private boolean darkFrameSubtraction = true;
    private boolean redNightVision = true;
    private int intervalSequenceShots;
    private int intervalSequenceSeconds;
    private double captureExposureBiasStops;
    private OpticsPreset preset = OpticsPreset.fromConfig(SnapshotConfig.get().defaultPreset);

    public CameraControl selected() {
        return selected;
    }

    public void selectNext() {
        selected = selected.next();
    }

    public void selectPrevious() {
        selected = selected.previous();
    }

    public void select(CameraControl control) {
        selected = control;
    }

    public void adjust(int direction) {
        switch (selected) {
            case ISO -> isoIndex = clamp(isoIndex + direction, 0, ISO_VALUES.length - 1);
            case SHUTTER -> shutterIndex = clamp(shutterIndex + direction, 0, SHUTTER_LABELS.length - 1);
            case APERTURE -> apertureIndex = clamp(apertureIndex + direction, 0, APERTURE_VALUES.length - 1);
            case EXPOSURE_MODE -> exposureMode = direction == 0 ? exposureMode : exposureMode.shift(direction);
            case METERING -> meteringMode = meteringMode.shift(direction);
            case AUTO_ISO -> autoIso = direction == 0 ? autoIso : !autoIso;
            case AUTO_ISO_MAX -> autoIsoMaximumIndex = clamp(autoIsoMaximumIndex + direction, 1, ISO_VALUES.length - 1);
            case MIN_SHUTTER -> minimumShutterIndex = clamp(minimumShutterIndex + direction, 0, MIN_SHUTTER_LABELS.length - 1);
            case BRACKETING -> exposureBracket = exposureBracket.shift(direction);
            case CAPTURE_TECHNIQUE -> captureTechnique = captureTechnique.shift(direction);
            case FOCAL_LENGTH -> adjustFocalLength(direction * 5.0);
            case LENS -> selectLens(lens.shift(direction));
            case FOCUS_MODE -> {
                if (direction != 0) {
                    autoFocus = !autoFocus;
                }
            }
            case FOCUS_DISTANCE -> {
                autoFocus = false;
                adjustFocusDistance(direction);
            }
            case FOCUS_POINT -> focusPointIndex = Math.floorMod(focusPointIndex + Integer.signum(direction), 9);
            case EXPOSURE_COMPENSATION -> exposureCompensation = clamp(exposureCompensation + direction, -6, 6);
            case WHITE_BALANCE -> whiteBalanceIndex = clamp(whiteBalanceIndex + direction, 0, WB_VALUES.length - 1);
            case CONTRAST -> contrast = clamp(contrast + direction, -5, 5);
            case SATURATION -> saturation = clamp(saturation + direction, -5, 5);
            case FILM_PROFILE -> filmProfile = filmProfile.shift(direction);
            case ASPECT_RATIO -> aspectRatio = aspectRatio.shift(direction);
            case CAMERA_ROLL -> adjustRoll(direction);
            case FILTER -> filter = filter.shift(direction);
            case PRINT_SIZE -> printSize = printSize.shift(direction);
            case STAR_TRACKING -> astroTracking = astroTracking.shift(direction);
            case ASTRO_STACK -> astroStackMode = astroStackMode.shift(direction);
            case EXPOSURE_ASSIST -> exposureAssist = exposureAssist.shift(direction);
            case MOOD -> mood = mood.shift(direction);
            case PRESET -> {
                if (direction != 0) {
                    preset = direction > 0 ? preset.next() : preset.previous();
                }
            }
        }
    }

    public void adjustFocalLength(double amount) {
        focalLength = clamp(focalLength + amount, lens.minimumFocalLength(), lens.maximumFocalLength());
    }

    public void adjustRoll(double amount) {
        rollDegrees = clamp(rollDegrees + amount, -30.0, 30.0);
    }

    public void resetRoll() {
        rollDegrees = 0.0;
    }

    private void adjustFocusDistance(int direction) {
        double step = focusDistance < 2.0 ? 0.10
            : focusDistance < 10.0 ? 0.50
            : focusDistance < 50.0 ? 2.0 : 10.0;
        focusDistance = clamp(focusDistance + Integer.signum(direction) * step,
            lens.minimumFocusDistance(), 256.0);
    }

    public void cycleFilmProfile() {
        filmProfile = filmProfile.shift(1);
    }

    public void cycleAspectRatio() {
        aspectRatio = aspectRatio.shift(1);
    }

    public void cycleMood() {
        mood = mood.shift(1);
    }

    public void cycleExposureMode() {
        exposureMode = exposureMode.next();
    }

    public void setExposureMode(ExposureMode mode) {
        exposureMode = mode;
    }

    public void setLens(LensProfile selectedLens) {
        selectLens(selectedLens);
    }

    public void setFilter(OpticalFilter selectedFilter) {
        filter = selectedFilter;
    }

    public void setPrintSize(PrintSize selectedPrintSize) {
        printSize = selectedPrintSize;
    }

    public void setPreset(OpticsPreset selectedPreset) {
        preset = selectedPreset;
    }

    public void setFocusPoint(int column, int row) {
        int clampedColumn = clamp(column, -1, 1);
        int clampedRow = clamp(row, -1, 1);
        focusPointIndex = (clampedRow + 1) * 3 + clampedColumn + 1;
    }

    public void setFocusDistance(double distance) {
        focusDistance = clamp(distance, lens.minimumFocusDistance(), 256.0);
    }

    public void setAutoFocus(boolean enabled) {
        autoFocus = enabled;
    }

    public void toggleAutoIso() {
        autoIso = !autoIso;
    }

    public void setExposureBracket(ExposureBracket bracket) {
        exposureBracket = bracket;
    }

    public void setCaptureTechnique(CaptureTechnique technique) {
        captureTechnique = technique;
    }

    public void cycleExposureAssist() {
        exposureAssist = exposureAssist.shift(1);
    }

    public void toggleAstrophotography() {
        astrophotography = !astrophotography;
        if (!astrophotography) {
            if (mood == MoodPreset.ASTROPHOTOGRAPHY) {
                mood = MoodPreset.NATURAL;
            }
            astroTracking = AstroTracking.OFF;
            return;
        }
        exposureMode = ExposureMode.MANUAL;
        isoIndex = 5;
        shutterIndex = 17;
        apertureIndex = 2;
        whiteBalanceIndex = 3;
        autoFocus = false;
        focusDistance = 256.0;
        flash = false;
        burst = false;
        filmProfile = FilmProfile.NEUTRAL;
        mood = MoodPreset.ASTROPHOTOGRAPHY;
        astroTracking = AstroTracking.SIDEREAL;
        selectLens(LensProfile.WIDE_PRIME);
    }

    public void applyAstrophotographyPreset() {
        if (!astrophotography) {
            toggleAstrophotography();
        } else {
            exposureMode = ExposureMode.MANUAL;
            isoIndex = 5;
            shutterIndex = 17;
            apertureIndex = 2;
            whiteBalanceIndex = 3;
            autoFocus = false;
            focusDistance = 256.0;
            mood = MoodPreset.ASTROPHOTOGRAPHY;
            astroTracking = AstroTracking.SIDEREAL;
            selectLens(LensProfile.WIDE_PRIME);
        }
    }

    public void applyMoonPreset() {
        astrophotography = true;
        exposureMode = ExposureMode.MANUAL;
        isoIndex = 1;
        shutterIndex = 6;
        apertureIndex = 7;
        whiteBalanceIndex = 4;
        autoFocus = false;
        focusDistance = 256.0;
        flash = false;
        burst = false;
        mood = MoodPreset.ASTROPHOTOGRAPHY;
        astroTracking = AstroTracking.LUNAR;
        selectLens(LensProfile.TELEPHOTO_ZOOM);
        focalLength = 200.0;
    }

    public void setAstroTracking(AstroTracking tracking) {
        astroTracking = tracking;
    }

    public void setAstroStackMode(AstroStackMode mode) {
        astroStackMode = mode;
    }

    public void toggleDarkFrameSubtraction() {
        darkFrameSubtraction = !darkFrameSubtraction;
    }

    public void toggleRedNightVision() {
        redNightVision = !redNightVision;
    }

    public void setIntervalSequence(int shots, int seconds) {
        intervalSequenceShots = Math.max(0, Math.min(99, shots));
        intervalSequenceSeconds = Math.max(0, seconds);
    }

    public void applyAutoExposure(double requiredStops) {
        if (exposureMode == ExposureMode.MANUAL || astrophotography) {
            return;
        }

        double bestScore = Double.POSITIVE_INFINITY;
        int bestIso = isoIndex;
        int bestShutter = shutterIndex;
        int bestAperture = apertureIndex;
        boolean variableIso = exposureMode == ExposureMode.AUTO || autoIso;
        int isoStart = variableIso ? 0 : isoIndex;
        int isoEnd = variableIso ? autoIsoMaximumIndex : isoIndex;
        int shutterStart = exposureMode == ExposureMode.SHUTTER_PRIORITY ? shutterIndex : 0;
        int shutterEnd = exposureMode == ExposureMode.SHUTTER_PRIORITY ? shutterIndex : SHUTTER_SECONDS.length - 2;
        int apertureStart = exposureMode == ExposureMode.APERTURE_PRIORITY ? apertureIndex : 0;
        int apertureEnd = exposureMode == ExposureMode.APERTURE_PRIORITY ? apertureIndex : APERTURE_VALUES.length - 1;

        for (int candidateIso = isoStart; candidateIso <= isoEnd; candidateIso++) {
            for (int candidateShutter = shutterStart; candidateShutter <= shutterEnd; candidateShutter++) {
                for (int candidateAperture = apertureStart; candidateAperture <= apertureEnd; candidateAperture++) {
                    double error = Math.abs(exposureStopsFor(candidateIso, candidateShutter, candidateAperture) - requiredStops);
                    double qualityPenalty = Math.max(0.0, log2(ISO_VALUES[candidateIso] / 100.0)) * 0.025;
                    qualityPenalty += Math.abs(APERTURE_VALUES[candidateAperture] - 4.0) * 0.003;
                    if (variableIso && exposureMode != ExposureMode.SHUTTER_PRIORITY
                        && SHUTTER_SECONDS[candidateShutter] > MIN_SHUTTER_SECONDS[minimumShutterIndex]) {
                        qualityPenalty += log2(SHUTTER_SECONDS[candidateShutter] / MIN_SHUTTER_SECONDS[minimumShutterIndex]) * 0.22;
                    }
                    if (exposureMode == ExposureMode.AUTO && SHUTTER_SECONDS[candidateShutter] > 1.0 / 30.0) {
                        qualityPenalty += log2(SHUTTER_SECONDS[candidateShutter] / (1.0 / 30.0)) * 0.018;
                    }
                    double score = error + qualityPenalty;
                    if (score < bestScore) {
                        bestScore = score;
                        bestIso = candidateIso;
                        bestShutter = candidateShutter;
                        bestAperture = candidateAperture;
                    }
                }
            }
        }
        isoIndex = bestIso;
        shutterIndex = bestShutter;
        apertureIndex = bestAperture;
    }

    private void selectLens(LensProfile selectedLens) {
        lens = selectedLens;
        focalLength = selectedLens.defaultFocalLength();
        focusDistance = clamp(focusDistance, selectedLens.minimumFocusDistance(), 256.0);
    }

    public void setAutoFocusDistance(double distance) {
        if (autoFocus) {
            focusDistance = clamp(distance, lens.minimumFocusDistance(), 256.0);
        }
    }

    public void toggleAutoFocus() {
        autoFocus = !autoFocus;
    }

    public void toggleFlash() {
        flash = !flash;
    }

    public void toggleBurst() {
        burst = !burst;
    }

    public void reset() {
        selected = CameraControl.EXPOSURE_MODE;
        isoIndex = 1;
        shutterIndex = 6;
        apertureIndex = 2;
        focalLength = 50.0;
        focusDistance = 18.0;
        exposureCompensation = 0;
        whiteBalanceIndex = 8;
        contrast = 0;
        saturation = 0;
        autoFocus = true;
        flash = false;
        burst = false;
        astrophotography = false;
        filmProfile = FilmProfile.NEUTRAL;
        aspectRatio = AspectRatio.NATIVE;
        mood = MoodPreset.NATURAL;
        rollDegrees = 0.0;
        focusPointIndex = 4;
        exposureMode = ExposureMode.MANUAL;
        meteringMode = MeteringMode.EVALUATIVE;
        autoIso = true;
        autoIsoMaximumIndex = 5;
        minimumShutterIndex = 2;
        exposureBracket = ExposureBracket.OFF;
        captureTechnique = CaptureTechnique.SINGLE;
        exposureAssist = ExposureAssist.HISTOGRAM;
        lens = LensProfile.KIT_ZOOM;
        filter = OpticalFilter.NONE;
        printSize = PrintSize.ONE_BY_ONE;
        astroTracking = AstroTracking.OFF;
        astroStackMode = AstroStackMode.DEEP_SKY;
        darkFrameSubtraction = true;
        redNightVision = true;
        intervalSequenceShots = 0;
        intervalSequenceSeconds = 0;
        captureExposureBiasStops = 0.0;
        preset = OpticsPreset.fromConfig(SnapshotConfig.get().defaultPreset);
    }

    public int iso() {
        return ISO_VALUES[isoIndex];
    }

    public String shutter() {
        return SHUTTER_LABELS[shutterIndex];
    }

    public double shutterSeconds() {
        return SHUTTER_SECONDS[shutterIndex];
    }

    public boolean bulb() {
        return shutterIndex == SHUTTER_LABELS.length - 1;
    }

    public boolean longExposure() {
        return shutterSeconds() >= 1.0;
    }

    public String aperture() {
        return formatDecimal(APERTURE_VALUES[apertureIndex]);
    }

    public double apertureNumber() {
        return APERTURE_VALUES[apertureIndex];
    }

    public int focalLength() {
        return (int) Math.round(focalLength);
    }

    public double focalLengthPrecise() {
        return focalLength;
    }

    public double focusDistance() {
        return focusDistance;
    }

    public String focusDistanceLabel() {
        if (focusDistance >= 100.0) {
            return "INF";
        }
        return String.format(Locale.ROOT, focusDistance < 10.0 ? "%.1fm" : "%.0fm", focusDistance);
    }

    public int exposureCompensation() {
        return exposureCompensation;
    }

    public float exposureCompensationStops() {
        return exposureCompensation / 2.0F;
    }

    public int whiteBalance() {
        return WB_VALUES[whiteBalanceIndex];
    }

    public int contrast() {
        return contrast;
    }

    public int saturation() {
        return saturation;
    }

    public boolean autoFocus() {
        return autoFocus;
    }

    public boolean flash() {
        return flash;
    }

    public boolean burst() {
        return burst;
    }

    public boolean astrophotography() {
        return astrophotography;
    }

    public OpticsPreset preset() {
        return preset;
    }

    public FilmProfile filmProfile() {
        return filmProfile;
    }

    public AspectRatio aspectRatio() {
        return aspectRatio;
    }

    public MoodPreset mood() {
        return mood;
    }

    public double rollDegrees() {
        return rollDegrees;
    }

    public ExposureMode exposureMode() {
        return exposureMode;
    }

    public MeteringMode meteringMode() {
        return meteringMode;
    }

    public boolean autoIso() {
        return autoIso;
    }

    public int autoIsoMaximum() {
        return ISO_VALUES[autoIsoMaximumIndex];
    }

    public String minimumShutter() {
        return MIN_SHUTTER_LABELS[minimumShutterIndex];
    }

    public ExposureBracket exposureBracket() {
        return exposureBracket;
    }

    public CaptureTechnique captureTechnique() {
        return captureTechnique;
    }

    public ExposureAssist exposureAssist() {
        return exposureAssist;
    }

    public LensProfile lens() {
        return lens;
    }

    public OpticalFilter filter() {
        return filter;
    }

    public PrintSize printSize() {
        return printSize;
    }

    public AstroTracking astroTracking() {
        return astroTracking;
    }

    public AstroStackMode astroStackMode() {
        return astroStackMode;
    }

    public boolean darkFrameSubtraction() {
        return darkFrameSubtraction;
    }

    public boolean redNightVision() {
        return redNightVision;
    }

    public int intervalSequenceShots() {
        return intervalSequenceShots;
    }

    public int intervalSequenceSeconds() {
        return intervalSequenceSeconds;
    }

    public double captureExposureBiasStops() {
        return captureExposureBiasStops;
    }

    void setCaptureExposureBiasStops(double stops) {
        captureExposureBiasStops = stops;
    }

    public int focusPointX() {
        return focusPointIndex % 3 - 1;
    }

    public int focusPointY() {
        return focusPointIndex / 3 - 1;
    }

    public String focusPointLabel() {
        if (focusPointIndex == 4) {
            return "CENTER";
        }
        return (focusPointY() < 0 ? "TOP" : focusPointY() > 0 ? "BOTTOM" : "MID")
            + "-" + (focusPointX() < 0 ? "LEFT" : focusPointX() > 0 ? "RIGHT" : "CENTER");
    }

    public double aperturePupilMillimeters() {
        return focalLength / apertureNumber();
    }

    public double targetFov() {
        double degrees = Math.toDegrees(2.0 * Math.atan(36.0 / (2.0 * focalLength)));
        return clamp(degrees, 10.0, 96.0);
    }

    public double exposureStops() {
        double isoStops = log2(iso() / 100.0);
        double shutterStops = log2(shutterSeconds() / (1.0 / 125.0));
        double apertureStops = 2.0 * log2(1.8 / apertureNumber());
        return isoStops + shutterStops + apertureStops + exposureCompensationStops()
            + captureExposureBiasStops + filter.exposureStops();
    }

    public double exposureMultiplier() {
        return exposureMultiplier(shutterSeconds());
    }

    public double exposureMultiplier(double actualShutterSeconds) {
        double isoStops = log2(iso() / 100.0);
        double shutterStops = log2(Math.max(1.0 / 8000.0, actualShutterSeconds) / (1.0 / 125.0));
        double apertureStops = 2.0 * log2(1.8 / apertureNumber());
        double stops = isoStops + shutterStops + apertureStops
            + exposureCompensationStops() + captureExposureBiasStops + filter.exposureStops();
        if (astrophotography) {
            // Renderer output is already display-referred. A two-second reference keeps short test
            // exposures usable while longer tracked exposures still gain light stop by stop.
            stops -= ASTRO_REFERENCE_STOPS;
        }
        return clamp(Math.pow(2.0, stops), astrophotography ? 1.0 / 64.0 : 0.125, 8.0);
    }

    public String metadata() {
        return "ISO " + iso()
            + " | " + shutter()
            + " | f/" + aperture()
            + " | " + exposureMode.label() + " " + meteringMode.label()
            + " | " + (autoIso ? "AUTO ISO<=" + autoIsoMaximum() + " MIN " + minimumShutter() : "FIXED ISO")
            + (exposureBracket.enabled() ? " | " + exposureBracket.label() : "")
            + (captureTechnique != CaptureTechnique.SINGLE ? " | " + captureTechnique.label() : "")
            + " | " + focalLength() + "mm"
            + " " + lens.label()
            + " | " + (autoFocus ? "AF " : "MF ") + focusDistanceLabel()
            + " " + focusPointLabel()
            + " | WB " + whiteBalance() + "K"
            + " | EV " + signed(exposureCompensationStops())
            + " | " + filmProfile.label()
            + " | " + aspectRatio.label()
            + " | Roll " + signed((float) rollDegrees) + "deg"
            + " | " + mood.label()
            + " | " + filter.label()
            + " | PRINT " + printSize.label()
            + (astroTracking != AstroTracking.OFF ? " | " + astroTracking.label() : "")
            + (astrophotography ? " | STACK " + astroStackMode.label() : "")
            + (astrophotography && darkFrameSubtraction ? " | AUTO DARK" : "")
            + (intervalSequenceShots > 0 ? " | INTERVAL " + intervalSequenceShots + "x" + intervalSequenceSeconds + "s" : "")
            + (Math.abs(captureExposureBiasStops) > 0.001 ? " | BRACKET " + signed((float) captureExposureBiasStops) : "")
            + (astrophotography ? " | ASTRO" : "")
            + " | " + preset.label();
    }

    public String valueFor(CameraControl control) {
        return switch (control) {
            case ISO -> Integer.toString(iso());
            case SHUTTER -> shutter();
            case APERTURE -> "f/" + aperture();
            case EXPOSURE_MODE -> exposureMode.label();
            case METERING -> meteringMode.label();
            case AUTO_ISO -> autoIso ? "ON" : "OFF";
            case AUTO_ISO_MAX -> Integer.toString(autoIsoMaximum());
            case MIN_SHUTTER -> minimumShutter();
            case BRACKETING -> exposureBracket.label();
            case CAPTURE_TECHNIQUE -> captureTechnique.label();
            case FOCAL_LENGTH -> focalLength() + "mm";
            case LENS -> lens.label();
            case FOCUS_MODE -> autoFocus ? "AF" : "MF";
            case FOCUS_DISTANCE -> (autoFocus ? "AF " : "MF ") + focusDistanceLabel();
            case FOCUS_POINT -> focusPointLabel();
            case EXPOSURE_COMPENSATION -> signed(exposureCompensationStops());
            case WHITE_BALANCE -> whiteBalance() + "K";
            case CONTRAST -> signed(contrast);
            case SATURATION -> signed(saturation);
            case FILM_PROFILE -> filmProfile.label();
            case ASPECT_RATIO -> aspectRatio.label();
            case CAMERA_ROLL -> signed((float) rollDegrees) + "deg";
            case FILTER -> filter.label();
            case PRINT_SIZE -> printSize.label();
            case STAR_TRACKING -> astroTracking.label();
            case ASTRO_STACK -> astroStackMode.label();
            case EXPOSURE_ASSIST -> exposureAssist.label();
            case MOOD -> mood.label();
            case PRESET -> preset.label();
        };
    }

    public CameraSettings copy() {
        CameraSettings copy = new CameraSettings();
        copy.selected = selected;
        copy.isoIndex = isoIndex;
        copy.shutterIndex = shutterIndex;
        copy.apertureIndex = apertureIndex;
        copy.focalLength = focalLength;
        copy.focusDistance = focusDistance;
        copy.exposureCompensation = exposureCompensation;
        copy.whiteBalanceIndex = whiteBalanceIndex;
        copy.contrast = contrast;
        copy.saturation = saturation;
        copy.autoFocus = autoFocus;
        copy.flash = flash;
        copy.burst = burst;
        copy.astrophotography = astrophotography;
        copy.filmProfile = filmProfile;
        copy.aspectRatio = aspectRatio;
        copy.mood = mood;
        copy.rollDegrees = rollDegrees;
        copy.focusPointIndex = focusPointIndex;
        copy.exposureMode = exposureMode;
        copy.meteringMode = meteringMode;
        copy.autoIso = autoIso;
        copy.autoIsoMaximumIndex = autoIsoMaximumIndex;
        copy.minimumShutterIndex = minimumShutterIndex;
        copy.exposureBracket = exposureBracket;
        copy.captureTechnique = captureTechnique;
        copy.exposureAssist = exposureAssist;
        copy.lens = lens;
        copy.filter = filter;
        copy.printSize = printSize;
        copy.astroTracking = astroTracking;
        copy.astroStackMode = astroStackMode;
        copy.darkFrameSubtraction = darkFrameSubtraction;
        copy.redNightVision = redNightVision;
        copy.intervalSequenceShots = intervalSequenceShots;
        copy.intervalSequenceSeconds = intervalSequenceSeconds;
        copy.captureExposureBiasStops = captureExposureBiasStops;
        copy.preset = preset;
        return copy;
    }

    public void applyFrom(CameraSettings source) {
        if (source == null) {
            return;
        }
        CameraSettings copy = source.copy();
        selected = copy.selected == null ? CameraControl.EXPOSURE_MODE : copy.selected;
        isoIndex = clamp(copy.isoIndex, 0, ISO_VALUES.length - 1);
        shutterIndex = clamp(copy.shutterIndex, 0, SHUTTER_LABELS.length - 1);
        apertureIndex = clamp(copy.apertureIndex, 0, APERTURE_VALUES.length - 1);
        exposureCompensation = clamp(copy.exposureCompensation, -6, 6);
        whiteBalanceIndex = clamp(copy.whiteBalanceIndex, 0, WB_VALUES.length - 1);
        contrast = clamp(copy.contrast, -5, 5);
        saturation = clamp(copy.saturation, -5, 5);
        autoFocus = copy.autoFocus;
        flash = copy.flash;
        burst = copy.burst;
        astrophotography = copy.astrophotography;
        filmProfile = copy.filmProfile == null ? FilmProfile.NEUTRAL : copy.filmProfile;
        aspectRatio = copy.aspectRatio == null ? AspectRatio.NATIVE : copy.aspectRatio;
        mood = copy.mood == null ? MoodPreset.NATURAL : copy.mood;
        rollDegrees = clamp(copy.rollDegrees, -30.0, 30.0);
        focusPointIndex = clamp(copy.focusPointIndex, 0, 8);
        exposureMode = copy.exposureMode == null ? ExposureMode.MANUAL : copy.exposureMode;
        meteringMode = copy.meteringMode == null ? MeteringMode.EVALUATIVE : copy.meteringMode;
        autoIso = copy.autoIso;
        autoIsoMaximumIndex = clamp(copy.autoIsoMaximumIndex, 1, ISO_VALUES.length - 1);
        minimumShutterIndex = clamp(copy.minimumShutterIndex, 0, MIN_SHUTTER_LABELS.length - 1);
        exposureBracket = copy.exposureBracket == null ? ExposureBracket.OFF : copy.exposureBracket;
        captureTechnique = copy.captureTechnique == null ? CaptureTechnique.SINGLE : copy.captureTechnique;
        exposureAssist = copy.exposureAssist == null ? ExposureAssist.HISTOGRAM : copy.exposureAssist;
        lens = copy.lens == null ? LensProfile.KIT_ZOOM : copy.lens;
        filter = copy.filter == null ? OpticalFilter.NONE : copy.filter;
        printSize = copy.printSize == null ? PrintSize.ONE_BY_ONE : copy.printSize;
        astroTracking = copy.astroTracking == null ? AstroTracking.OFF : copy.astroTracking;
        astroStackMode = copy.astroStackMode == null ? AstroStackMode.DEEP_SKY : copy.astroStackMode;
        darkFrameSubtraction = copy.darkFrameSubtraction;
        redNightVision = copy.redNightVision;
        intervalSequenceShots = clamp(copy.intervalSequenceShots, 0, 99);
        intervalSequenceSeconds = Math.max(0, copy.intervalSequenceSeconds);
        captureExposureBiasStops = clamp(copy.captureExposureBiasStops, -6.0, 6.0);
        preset = copy.preset == null ? OpticsPreset.fromConfig(SnapshotConfig.get().defaultPreset) : copy.preset;
        focalLength = clamp(copy.focalLength, lens.minimumFocalLength(), lens.maximumFocalLength());
        focusDistance = clamp(copy.focusDistance, lens.minimumFocusDistance(), 256.0);
    }

    private double exposureStopsFor(int candidateIso, int candidateShutter, int candidateAperture) {
        double isoStops = log2(ISO_VALUES[candidateIso] / 100.0);
        double shutterStops = log2(SHUTTER_SECONDS[candidateShutter] / (1.0 / 125.0));
        double apertureStops = 2.0 * log2(1.8 / APERTURE_VALUES[candidateAperture]);
        return isoStops + shutterStops + apertureStops + exposureCompensationStops() + filter.exposureStops();
    }

    private static String formatDecimal(double value) {
        return value == Math.rint(value)
            ? Integer.toString((int) value)
            : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String signed(float value) {
        return String.format(Locale.ROOT, "%+.1f", value);
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
