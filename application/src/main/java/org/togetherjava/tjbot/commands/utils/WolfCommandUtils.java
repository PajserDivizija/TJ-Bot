package org.togetherjava.tjbot.commands.utils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.IntStream;

public class WolfCommandUtils {
    public static BufferedImage combineImages(List<BufferedImage> images, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics imgGraphics = image.getGraphics();
        int resultHeight = 0;
        for (BufferedImage img : images) {
            imgGraphics.drawImage(img, width, resultHeight, null);
            resultHeight += img.getHeight(null);
        }
        return image;
    }

    public static boolean compareImages(BufferedImage img1, BufferedImage img2) {
        int width1 = img1.getWidth();
        int height1 = img1.getHeight();
        return width1 == img2.getWidth() && height1 == img2.getHeight()
                && IntStream.range(0, width1)
                    .mapToObj(x -> IntStream.range(0, height1)
                        .anyMatch(y -> img1.getRGB(x, y) != img2.getRGB(x, y)))
                    .noneMatch(x -> x);
    }
}
