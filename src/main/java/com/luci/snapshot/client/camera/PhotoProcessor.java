package com.luci.snapshot.client.camera;

import com.mojang.blaze3d.platform.NativeImage;

final class PhotoProcessor {
    private PhotoProcessor() {
    }

    static void process(NativeImage image, CameraSettings settings, float yawVelocity, float pitchVelocity) {
        process(image, settings, yawVelocity, pitchVelocity, null, false, settings.shutterSeconds());
    }

    static void process(NativeImage image, CameraSettings settings, float yawVelocity, float pitchVelocity,
                        SceneDepthMap depthMap, boolean liveFilmBaked, double actualExposureSeconds) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] source = image.getPixels();
        double exposure = settings.exposureMultiplier(actualExposureSeconds);
        double temperature = liveFilmBaked ? 0.0
            : (settings.whiteBalance() + settings.mood().temperatureOffset() - 6500.0) / 7500.0;
        double redBalance = 1.0 + temperature * 0.22;
        double blueBalance = 1.0 - temperature * 0.25;
        double contrast = liveFilmBaked ? 1.0 : (1.0 + settings.contrast() * 0.08) * settings.mood().contrast();
        double saturation = liveFilmBaked ? 1.0 : (1.0 + settings.saturation() * 0.10) * settings.mood().saturation();
        double focusFalloff = clamp(Math.sqrt(18.0 / settings.focusDistance()), 0.60, 1.55);
        double normalizedPupil = clamp((settings.aperturePupilMillimeters() - 3.0) / 38.0, 0.0, 1.0);
        double apertureStrength = Math.pow(normalizedPupil, 0.72)
            * settings.preset().depthOfField() * focusFalloff;
        apertureStrength = clamp(apertureStrength, 0.0, 1.0);
        double shutterMotion = settings.shutterSeconds() * settings.preset().motionBlur();
        int motionX = clamp((int) Math.round(yawVelocity * shutterMotion * width / 28.0), -18, 18);
        int motionY = clamp((int) Math.round(pitchVelocity * shutterMotion * height / 20.0), -14, 14);
        double grain = settings.preset().grain() * Math.max(0.0, log2(settings.iso() / 100.0)) * 2.2;
        double chromatic = settings.preset().chromaticAberration() * 2.8;
        double bloom = settings.preset().bloom() * settings.mood().bloom();

        if (settings.filter() == OpticalFilter.POLARIZER) {
            saturation *= 1.12;
        } else if (settings.filter() == OpticalFilter.DIFFUSION) {
            bloom *= 1.65;
            contrast *= 0.94;
        }

        switch (settings.filmProfile()) {
            case WARM_400 -> {
                if (!liveFilmBaked) {
                    saturation *= 0.94;
                    contrast *= 0.96;
                }
                grain += 0.55;
            }
            case MUTED_CHROME -> {
                if (!liveFilmBaked) {
                    saturation *= 0.78;
                    contrast *= 1.10;
                }
                grain += 0.35;
            }
            case MONOCHROME -> {
                if (!liveFilmBaked) {
                    contrast *= 1.08;
                }
                grain += 0.80;
            }
            case NEUTRAL -> {
            }
        }

        double centerX = (width - 1) * 0.5;
        double centerY = (height - 1) * 0.5;
        double maxRadius = Math.sqrt(centerX * centerX + centerY * centerY);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double nx = (x - centerX) / Math.max(1.0, centerX);
                double ny = (y - centerY) / Math.max(1.0, centerY);
                double radial = Math.sqrt(nx * nx + ny * ny);
                double sharpRadius = clamp(0.19 + settings.focusDistance() / 220.0, 0.19, 0.38);
                double blurPixels;
                if (liveFilmBaked || settings.captureTechnique() != CaptureTechnique.SINGLE) {
                    blurPixels = 0.0;
                } else if (depthMap != null) {
                    double subjectDistance = depthMap.sample(x / (double) Math.max(1, width - 1), y / (double) Math.max(1, height - 1));
                    blurPixels = circleOfConfusionPixels(settings, subjectDistance, width)
                        * settings.preset().depthOfField() * 0.68;
                } else {
                    blurPixels = apertureStrength * smoothstep(sharpRadius, 0.94, radial) * 5.0;
                }
                int localApertureRadius = clamp((int) Math.round(blurPixels), 0,
                    settings.preset() == OpticsPreset.SCREENSHOT_ULTRA ? 14 : 10);
                double focusBlend = clamp(blurPixels / 6.0, 0.0, 1.0);

                double distortionScale = 1.0 + settings.lens().distortion() * radial * radial;
                int sourceX = clamp((int) Math.round(centerX + (x - centerX) * distortionScale), 0, width - 1);
                int sourceY = clamp((int) Math.round(centerY + (y - centerY) * distortionScale), 0, height - 1);
                int base = source[sourceY * width + sourceX];
                double red = (base >> 16) & 0xFF;
                double green = (base >> 8) & 0xFF;
                double blue = base & 0xFF;

                if (motionX != 0 || motionY != 0) {
                    int before = sample(source, width, height, x - motionX, y - motionY);
                    int after = sample(source, width, height, x + motionX, y + motionY);
                    red = (red * 2.0 + ((before >> 16) & 0xFF) + ((after >> 16) & 0xFF)) * 0.25;
                    green = (green * 2.0 + ((before >> 8) & 0xFF) + ((after >> 8) & 0xFF)) * 0.25;
                    blue = (blue * 2.0 + (before & 0xFF) + (after & 0xFF)) * 0.25;
                }

                if (focusBlend > 0.002 && localApertureRadius > 0) {
                    int left = sample(source, width, height, sourceX - localApertureRadius, sourceY);
                    int right = sample(source, width, height, sourceX + localApertureRadius, sourceY);
                    int up = sample(source, width, height, sourceX, sourceY - localApertureRadius);
                    int down = sample(source, width, height, sourceX, sourceY + localApertureRadius);
                    int diagonal = Math.max(1, (int) Math.round(localApertureRadius * 0.72));
                    int upperLeft = sample(source, width, height, sourceX - diagonal, sourceY - diagonal);
                    int upperRight = sample(source, width, height, sourceX + diagonal, sourceY - diagonal);
                    int lowerLeft = sample(source, width, height, sourceX - diagonal, sourceY + diagonal);
                    int lowerRight = sample(source, width, height, sourceX + diagonal, sourceY + diagonal);
                    double softRed = channelAverage(left, right, up, down, upperLeft, upperRight, lowerLeft, lowerRight, 16);
                    double softGreen = channelAverage(left, right, up, down, upperLeft, upperRight, lowerLeft, lowerRight, 8);
                    double softBlue = channelAverage(left, right, up, down, upperLeft, upperRight, lowerLeft, lowerRight, 0);
                    red = mix(red, softRed, focusBlend);
                    green = mix(green, softGreen, focusBlend);
                    blue = mix(blue, softBlue, focusBlend);
                }

                int channelShift = (int) Math.round(chromatic * smoothstep(0.45, 1.1, radial));
                if (channelShift > 0) {
                    int redSource = sample(source, width, height, x + channelShift, y);
                    int blueSource = sample(source, width, height, x - channelShift, y);
                    red = mix(red, (redSource >> 16) & 0xFF, 0.35);
                    blue = mix(blue, blueSource & 0xFF, 0.35);
                }

                red = sensorHighlightShoulder(red * exposure * redBalance);
                green = sensorHighlightShoulder(green * exposure);
                blue = sensorHighlightShoulder(blue * exposure * blueBalance);

                if (!liveFilmBaked) {
                    switch (settings.filmProfile()) {
                        case WARM_400 -> {
                            red = highlightRollOff(red * 1.045 + 3.0, 0.62);
                            green = highlightRollOff(green + 2.0, 0.70);
                            blue = highlightRollOff(blue * 0.94 + 5.0, 0.78);
                        }
                        case MUTED_CHROME -> {
                            red = red * 0.95 + 2.0;
                            green = green * 0.965 + 3.0;
                            blue = blue * 1.015 + 2.0;
                        }
                        case MONOCHROME -> {
                            double monochrome = red * 0.24 + green * 0.68 + blue * 0.08;
                            red = monochrome * 1.01;
                            green = monochrome;
                            blue = monochrome * 0.98;
                        }
                        case NEUTRAL -> {
                        }
                    }
                }

                if (!liveFilmBaked) {
                    switch (settings.mood()) {
                    case OVERCAST -> {
                        red = red * 0.96 + 7.0;
                        green = green * 0.99 + 9.0;
                        blue = blue * 1.04 + 11.0;
                    }
                    case ALPINE_SUNRISE -> {
                        red = red * 1.06 + 4.0;
                        green = green * 1.01 + 2.0;
                        blue = blue * 0.94 + 3.0;
                    }
                    case ETHEREAL_MIST -> {
                        red = red * 1.01 + 11.0;
                        green = green * 1.02 + 12.0;
                        blue = blue * 1.04 + 14.0;
                    }
                    case ASTROPHOTOGRAPHY -> {
                        red = red * 0.96 + 2.0;
                        green = green * 0.99 + 3.0;
                        blue = blue * 1.08 + 5.0;
                    }
                    case NATURAL -> {
                    }
                    }
                }

                if (settings.filter() == OpticalFilter.GRADUATED_ND) {
                    double gradient = mix(0.40, 1.0, smoothstep(0.0, 0.70, y / (double) height));
                    red *= gradient;
                    green *= gradient;
                    blue *= gradient;
                } else if (settings.filter() == OpticalFilter.POLARIZER) {
                    blue *= 1.05;
                    green *= 1.01;
                } else if (settings.filter() == OpticalFilter.INFRARED) {
                    double infrared = red * 0.58 + green * 0.40 + blue * 0.02;
                    red = infrared * 1.04;
                    green = infrared;
                    blue = infrared * 0.96;
                }

                if (settings.astrophotography()) {
                    red = astroStretch(red, 0.97);
                    green = astroStretch(green, 1.0);
                    blue = astroStretch(blue, 1.08);
                }

                double luminance = red * 0.2126 + green * 0.7152 + blue * 0.0722;
                red = luminance + (red - luminance) * saturation;
                green = luminance + (green - luminance) * saturation;
                blue = luminance + (blue - luminance) * saturation;
                red = (red - 127.5) * contrast + 127.5;
                green = (green - 127.5) * contrast + 127.5;
                blue = (blue - 127.5) * contrast + 127.5;

                if (luminance > 185.0) {
                    double glow = (luminance - 185.0) * bloom * 0.20;
                    red += glow;
                    green += glow;
                    blue += glow;
                }

                if (settings.flash()) {
                    double flashFalloff = clamp(1.0 - Math.sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)) / maxRadius, 0.0, 1.0);
                    double flashGain = flashFalloff * flashFalloff * 20.0;
                    red += flashGain;
                    green += flashGain;
                    blue += flashGain * 0.92;
                }

                double vignette = 1.0 - settings.preset().depthOfField() * 0.22 * Math.pow(clamp(radial, 0.0, 1.35), 2.1);
                red *= vignette;
                green *= vignette;
                blue *= vignette;

                if (grain > 0.0) {
                    int hash = x * 374761393 + y * 668265263;
                    hash = (hash ^ (hash >>> 13)) * 1274126177;
                    double noise = (((hash ^ (hash >>> 16)) & 0xFF) / 255.0 - 0.5) * grain * 2.0;
                    red += noise;
                    green += noise;
                    blue += noise;
                }

                if (settings.longExposure()) {
                    int hotPixelHash = x * 92837111 ^ y * 689287499;
                    if ((hotPixelHash & 0x7FFF) == 17) {
                        red += settings.astrophotography() ? 36.0 : 18.0;
                        blue += settings.astrophotography() ? 28.0 : 12.0;
                    }
                }

                red = outputHighlightShoulder(red);
                green = outputHighlightShoulder(green);
                blue = outputHighlightShoulder(blue);

                int alpha = (base >>> 24) & 0xFF;
                image.setPixel(x, y, (alpha << 24)
                    | (clamp((int) Math.round(red), 0, 255) << 16)
                    | (clamp((int) Math.round(green), 0, 255) << 8)
                    | clamp((int) Math.round(blue), 0, 255));
            }
        }
    }

    private static double circleOfConfusionPixels(CameraSettings settings, double subjectDistanceMeters, int imageWidth) {
        double focalLength = settings.focalLengthPrecise();
        double aperture = settings.apertureNumber();
        double subject = Math.max(focalLength + 1.0, subjectDistanceMeters * 1000.0);
        double focus = Math.max(focalLength + 1.0, settings.focusDistance() * 1000.0);
        double circle = Math.abs(focalLength * focalLength * (subject - focus)
            / (aperture * subject * (focus - focalLength)));
        return circle / 36.0 * imageWidth;
    }

    static NativeImage cropToAspect(NativeImage image, AspectRatio aspectRatio) {
        if (aspectRatio.nativeFrame()) {
            return image;
        }

        int sourceWidth = image.getWidth();
        int sourceHeight = image.getHeight();
        double sourceRatio = sourceWidth / (double) sourceHeight;
        double targetRatio = aspectRatio.ratio();
        int width = sourceWidth;
        int height = sourceHeight;
        if (sourceRatio > targetRatio) {
            width = Math.max(1, (int) Math.floor(sourceHeight * targetRatio));
        } else {
            height = Math.max(1, (int) Math.floor(sourceWidth / targetRatio));
        }
        if (width == sourceWidth && height == sourceHeight) {
            return image;
        }

        int offsetX = (sourceWidth - width) / 2;
        int offsetY = (sourceHeight - height) / 2;
        NativeImage cropped = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                cropped.setPixel(x, y, image.getPixel(offsetX + x, offsetY + y));
            }
        }
        return cropped;
    }

    private static double channelAverage(int a, int b, int c, int d, int e, int f, int g, int h, int shift) {
        return (((a >> shift) & 0xFF) + ((b >> shift) & 0xFF) + ((c >> shift) & 0xFF) + ((d >> shift) & 0xFF)
            + ((e >> shift) & 0xFF) + ((f >> shift) & 0xFF) + ((g >> shift) & 0xFF) + ((h >> shift) & 0xFF)) / 8.0;
    }

    private static double highlightRollOff(double value, double strength) {
        if (value <= 188.0) {
            return value;
        }
        double highlight = value - 188.0;
        return 188.0 + highlight / (1.0 + highlight * strength / 90.0);
    }

    private static double sensorHighlightShoulder(double value) {
        if (value <= 185.0) {
            return value;
        }
        double highlight = value - 185.0;
        return 185.0 + highlight / (1.0 + highlight / 85.0);
    }

    private static double astroStretch(double value, double colorGain) {
        double normalized = clamp(value / 255.0, 0.0, 1.0);
        return Math.pow(normalized, 0.72) * 255.0 * colorGain;
    }

    private static double outputHighlightShoulder(double value) {
        if (value <= 230.0) {
            return value;
        }
        double highlight = value - 230.0;
        return 230.0 + highlight / (1.0 + highlight / 24.0);
    }

    private static int sample(int[] pixels, int width, int height, int x, int y) {
        int clampedX = clamp(x, 0, width - 1);
        int clampedY = clamp(y, 0, height - 1);
        return pixels[clampedY * width + clampedX];
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double mix(double start, double end, double amount) {
        return start + (end - start) * amount;
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
