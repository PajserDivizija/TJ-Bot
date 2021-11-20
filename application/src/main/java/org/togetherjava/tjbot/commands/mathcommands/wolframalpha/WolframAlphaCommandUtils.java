package org.togetherjava.tjbot.commands.mathcommands.wolframalpha;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageUpdateAction;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

enum WolframAlphaCommandUtils {
    ;

    static byte[] imageToBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    static BufferedImage combineImages(List<BufferedImage> images, int height) {
        int width =
                images.stream().mapToInt(BufferedImage::getWidth).max().orElse(Integer.MAX_VALUE);
        // height = images.stream().mapToInt(BufferedImage::getHeight).sum();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics imgGraphics = image.getGraphics();
        imgGraphics.setColor(Color.WHITE);
        imgGraphics.fillRect(0, 0, width, height);
        int resultHeight = 0;
        for (BufferedImage img : images) {
            imgGraphics.drawImage(img, 0, resultHeight, null);
            resultHeight += img.getHeight(null);
        }
        return image;
    }

    static boolean compareImages(BufferedImage img1, BufferedImage img2) {
        int width1 = img1.getWidth();
        int height1 = img1.getHeight();
        return width1 == img2.getWidth() && height1 == img2.getHeight()
                && IntStream.range(0, width1)
                    .mapToObj(x -> IntStream.range(0, height1)
                        .anyMatch(y -> img1.getRGB(x, y) != img2.getRGB(x, y)))
                    .noneMatch(x -> x);
    }

    static int getWidth(String header) {
        BufferedImage image = new BufferedImage(100, 100, 6);
        Graphics g = image.getGraphics();
        g.setFont(WolframAlphaCommand.WOLFRAM_ALPHA_FONT);
        return g.getFontMetrics().stringWidth(header);
    }

    static String handleMisunderstoodQuery(QueryResult result) {
        List<String> output = new ArrayList<>();
        output
            .add("The Wolfram|Alpha API was unable to produce a successful result. Visit the URI");
        Tips tips = result.getTips();
        if (tips != null) {
            output.add("Here are some tips \n"
                    + tips.getTips().stream().map(Tip::getText).collect(Collectors.joining("\n")));
        }
        FutureTopic futureTopic = result.getFutureTopic();
        if (futureTopic != null) {
            output.add(
                    "Your query is regarding The topic \"%s\" which might be supported by Wolfram Alpha in the future"
                        .formatted(futureTopic.getTopic()));
        }
        LanguageMessage languageMsg = result.getLanguageMessage();
        if (languageMsg != null) {
            output.add(languageMsg.getEnglish() + "\n" + languageMsg.getOther());
        }
        DidYouMeans didYouMeans = result.getDidYouMeans();
        if (didYouMeans != null) {
            output.add("Did you mean \n" + didYouMeans.getDidYouMeans()
                .stream()
                .map(DidYouMean::getMessage)
                .collect(Collectors.joining("\n")));
        }
        RelatedExamples relatedExamples = result.getRelatedExamples();
        if (relatedExamples != null) {
            output.add("Here are some related examples \n" + relatedExamples.getRelatedExamples()
                .stream()
                .map(RelatedExample::getCategoryThumb)
                .collect(Collectors.joining("\n")));
        }
        WolframAlphaCommand.logger.info("Error Message \n {}", String.join("\n", output));
        return String.join("\n", output);
    }

    static String handleError(QueryResult result) {
        WolframAlphaCommand.logger.debug("The handleError method is being executed");
        WolframAlphaCommand.logger.error(
                "Error getting response from Wolfram Alpha API: Error Code: {} Error Message: {}",
                result.getErrorTag().getCode(), result.getErrorTag().getMessage());
        return "An error occurred while getting response from the Wolfram|Alpha API. Check the URI";
    }

    static @NotNull Optional<QueryResult> parseQuery(@NotNull HttpResponse<String> response,
            @NotNull WebhookMessageUpdateAction<Message> action) {
        QueryResult result;
        try {
            Files.writeString(Path
                .of("C:\\Users\\Abc\\IdeaProjects\\TJ-Bot-baseRepo\\application\\src\\main\\java\\org\\togetherjava\\tjbot\\commands\\mathcommands\\wolframalpha\\responsebody.xml"),
                    response.body());
            result = WolframAlphaCommand.XML.readValue(response.body(), QueryResult.class);

        } catch (IOException e) {
            action.setContent("Unexpected response from WolframAlpha API").queue();
            WolframAlphaCommand.logger.error("Unable to deserialize the class ", e);
            return Optional.empty();
        }
        return Optional.of(result);
    }

    static @NotNull String handleSuccessfulResult(@NotNull QueryResult result,
            WebhookMessageUpdateAction<Message> action, MessageEmbed embed) {

        int filesAttached = 0;
        int resultHeight = 0;
        EmbedBuilder embedBuilder = new EmbedBuilder();
        List<MessageEmbed> embeds = new ArrayList<>(List.of(embed));
        List<BufferedImage> images = new ArrayList<>();
        List<Pod> pods = result.getPods();
        for (Pod pod : pods) {
            List<SubPod> subPods = pod.getSubPods();
            for (SubPod subPod : subPods) {
                WolframAlphaImage image = subPod.getImage();
                try {

                    String source = image.getSource();
                    String header = pod.getTitle();
                    boolean firstSubPod = subPod == subPods.get(0);
                    int width = (firstSubPod ? Math.max(getWidth(header), image.getWidth())
                            : image.getWidth()) + 10;
                    int height = image.getHeight();
                    if (firstSubPod)
                        height += WolframAlphaCommand.TEXT_HEIGHT;
                    BufferedImage readImage =
                            new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
                    Graphics graphics = readImage.getGraphics();
                    if (firstSubPod) {
                        graphics.setFont(WolframAlphaCommand.WOLFRAM_ALPHA_FONT);
                        graphics.setColor(Color.WHITE);
                        graphics.setColor(WolframAlphaCommand.WOLFRAM_ALPHA_TEXT_COLOR);
                        graphics.drawString(header, 10, 15);
                    }
                    BufferedImage srcImg = ImageIO.read(new URL(source));
                    // TODO use named constants
                    graphics.drawImage(srcImg, 10, firstSubPod ? 20 : 0, null);

                    if (filesAttached == WolframAlphaCommand.MAX_EMBEDS) {
                        action.setEmbeds(embeds);
                        return "Too many images. Visit the URI";
                    }


                    if (resultHeight
                            + image.getHeight() > WolframAlphaCommand.MAX_IMAGE_HEIGHT_PX) {
                        BufferedImage combinedImage =
                                WolframAlphaCommandUtils.combineImages(images, resultHeight);
                        images.clear();
                        action = action.addFile(
                                WolframAlphaCommandUtils.imageToBytes(combinedImage),
                                "result%d.png".formatted(++filesAttached));
                        resultHeight = 0;
                        embeds.add(embedBuilder
                            .setImage("attachment://result%d.png".formatted(filesAttached))
                            .build());
                    } else if (pod == pods.get(pods.size() - 1)
                            && subPod == subPods.get(subPods.size() - 1) && images.size() != 0) {
                        action = action.addFile(
                                WolframAlphaCommandUtils.imageToBytes(WolframAlphaCommandUtils
                                    .combineImages(images, resultHeight + image.getHeight())),
                                "result%d.png".formatted(++filesAttached));
                        images.clear();
                        embeds.add(embedBuilder
                            .setImage("attachment://result%d.png".formatted(filesAttached))
                            .build());
                    }
                    resultHeight += readImage.getHeight();
                    images.add(readImage);
                } catch (IOException e) {
                    WolframAlphaCommand.logger
                        .error("Failed to read image {} from the WolframAlpha response", image, e);
                    return "Unable to generate message based on the WolframAlpha response";
                }
            }
        }
        action.setEmbeds(embeds);
        return "";
    }
}
