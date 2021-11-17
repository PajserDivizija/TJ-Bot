package org.togetherjava.tjbot.commands.mathcommands.wolframalpha;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.mikael.urlbuilder.UrlBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageUpdateAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import org.togetherjava.tjbot.config.Config;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WolframAlphaCommand extends SlashCommandAdapter {
    public static final Logger logger = LoggerFactory.getLogger(WolframAlphaCommand.class);
    /**
     * Starting part of a regular wolframalpha query link.
     */
    public static final String USER_ENDPOINT = "https://www.wolframalpha.com/input";
    private static final int HTTP_STATUS_CODE_OK = 200;
    private static final XmlMapper XML = new XmlMapper();
    private static final String QUERY_OPTION = "query";
    /**
     * WolframAlpha API endpoint to connect to.
     *
     * @see <a href=
     *      "https://products.wolframalpha.com/docs/WolframAlpha-API-Reference.pdf">WolframAlpha API
     *      Reference</a>.
     */
    private static final String API_ENDPOINT = "http://api.wolframalpha.com/v2/query";
    private static final int MAX_IMAGE_HEIGHT_PX = 400;
    /**
     * WolframAlpha text Color
     */
    private static final Color WOLFRAM_ALPHA_TEXT_COLOR = Color.decode("#3C3C3C");
    /**
     * WolframAlpha Font
     */
    private static final Font WOLFRAM_ALPHA_FONT = new Font("Times", Font.PLAIN, 15)
        .deriveFont(Map.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON));
    /**
     * Height of the unscaled text displayed in Font {@link #WOLFRAM_ALPHA_FONT}
     */
    private static final int TEXT_HEIGHT = 30;
    /**
     * Maximum Embeds that can be sent in a {@link WebhookMessageUpdateAction}
     */
    public static final int MAX_EMBEDS = 10;
    private final HttpClient client = HttpClient.newHttpClient();

    public WolframAlphaCommand() {
        super("wolfram-alpha", "Renders mathematical queries using WolframAlpha",
                SlashCommandVisibility.GUILD);
        getData().addOption(OptionType.STRING, QUERY_OPTION, "the query to send to WolframAlpha",
                true);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {

        // The processing takes some time
        event.deferReply().queue();

        String query = Objects.requireNonNull(event.getOption(QUERY_OPTION)).getAsString();

        URI userUri = UrlBuilder.fromString(USER_ENDPOINT).addParameter("i", query).toUri();

        MessageEmbed uriEmbed = new EmbedBuilder()
            .setTitle(query + "- Wolfram|Alpha", userUri.toString())
            .setDescription(
                    "Wolfram|Alpha brings expert-level knowledge and capabilities to the broadest possible range of people-spanning all professions and education levels.")
            .build();

        WebhookMessageUpdateAction<Message> action =
                event.getHook().editOriginal("").setEmbeds(uriEmbed);

        URI apiUri = UrlBuilder.fromString(API_ENDPOINT)
            .addParameter("appid", Config.getInstance().getWolframAlphaAppId())
            .addParameter("format", "image,plaintext")
            .addParameter("input", query)
            .toUri();
        HttpRequest request = sendQuery(apiUri);
        Optional<HttpResponse<String>> optResponse = getResponse(request, action);
        if (optResponse.isEmpty())
            return;
        HttpResponse<String> response = optResponse.get();
        Optional<QueryResult> optResult = parseQuery(response, action);
        if (optResult.isEmpty())
            return;
        QueryResult result = optResult.get();
        action = action.setContent("Computed in:" + result.getTiming());
        String content;
        if (result.isError()) {
            content = WolframAlphaCommandUtils.handleError(result);
        } else if (!result.isSuccess()) {
            content = WolframAlphaCommandUtils.handleMisunderstoodQuery(result);
        } else {
            content = "Computed in:" + result.getTiming() + "\n"
                    + (result.getNumberOfTimedOutPods() == 0 ? ""
                            : "Some pods have timed out. Visit the URI")
                    + "\n" + handleSuccessfulResult(result, action, uriEmbed);
        }
        action.setContent(content).queue();
    }

    private @NotNull Optional<HttpResponse<String>> getResponse(@NotNull HttpRequest request, @NotNull WebhookMessageUpdateAction<Message> action) {
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            action.setContent("Unable to get a response from WolframAlpha API").queue();
            logger.warn("Could not get the response from the server", e);
            return Optional.empty();
        } catch (InterruptedException e) {
            action.setContent("Connection to WolframAlpha was interrupted").queue();
            logger.warn("Connection to WolframAlpha was interrupted", e);
            Thread.currentThread().interrupt();
            return Optional.empty();
        }

        if (response.statusCode() != HTTP_STATUS_CODE_OK) {
            action.setContent("The response' status code was incorrect").queue();
            logger.warn("Unexpected status code: Expected: {} Actual: {}", HTTP_STATUS_CODE_OK,
                    response.statusCode());
            return Optional.empty();
        }
        return Optional.of(response);
    }

    private @NotNull HttpRequest sendQuery(@NotNull URI uri) {
        logger.info("The query URI is {}", uri);
        return HttpRequest.newBuilder(uri).GET().build();
    }

    private @NotNull Optional<QueryResult> parseQuery(@NotNull HttpResponse<String> response, @NotNull WebhookMessageUpdateAction<Message> action) {
        QueryResult result;
        try {
            Files.writeString(Path
                .of("C:\\Users\\Abc\\IdeaProjects\\TJ-Bot-baseRepo\\application\\src\\main\\java\\org\\togetherjava\\tjbot\\commands\\mathcommands\\wolframalpha\\responsebody.xml"),
                    response.body());
            result = XML.readValue(response.body(), QueryResult.class);

        } catch (IOException e) {
            action.setContent("Unexpected response from WolframAlpha API").queue();
            logger.error("Unable to deserialize the class ", e);
            return Optional.empty();
        }
        return Optional.of(result);
    }
    private @NotNull String handleSuccessfulResult(@NotNull QueryResult result, WebhookMessageUpdateAction<Message> action,
            MessageEmbed embed) {

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
                    int width =
                            (firstSubPod ? Math.max(getWidth(header), image.getWidth()) : image.getWidth())
                                    + 10;
                    int height = image.getHeight();
                    if (firstSubPod)
                        height += TEXT_HEIGHT;
                    BufferedImage readImage =
                            new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
                    Graphics graphics = readImage.getGraphics();
                    if (firstSubPod) {
                        graphics.setFont(WOLFRAM_ALPHA_FONT);
                        graphics.setColor(Color.WHITE);
                        graphics.setColor(WOLFRAM_ALPHA_TEXT_COLOR);
                        graphics.drawString(header, 10, 15);
                    }
                    BufferedImage srcImg = ImageIO.read(new URL(source));
                    //TODO use named constants
                    graphics.drawImage(srcImg, 10, firstSubPod ? 20 : 0, null);

                    if (filesAttached == MAX_EMBEDS) {
                        action.setEmbeds(embeds);
                        return "Too many images. Visit the URI";
                    }


                    if (resultHeight + image.getHeight() > MAX_IMAGE_HEIGHT_PX) {
                        BufferedImage combinedImage = WolframAlphaCommandUtils.combineImages(images,
                                resultHeight);
                        images.clear();
                        ImageIO.write(combinedImage, "png", Path
                            .of("C:\\Users\\Abc\\IdeaProjects\\TJ-Bot-baseRepo\\application\\src\\main\\java\\org\\togetherjava\\tjbot\\commands\\mathcommands\\wolframalpha\\sentImage%d.png"
                                .formatted(++filesAttached))
                            .toFile());
                        action = action.addFile(
                                WolframAlphaCommandUtils.imageToBytes(combinedImage),
                                "result%d.png".formatted(++filesAttached));
                        resultHeight = 0;
                        embeds.add(embedBuilder
                            .setImage("attachment://result%d.png".formatted(filesAttached))
                            .build());
                    } else if (pod == pods.get(pods.size() - 1)
                            && subPod == subPods.get(subPods.size() - 1) && images.size() != 0) {
                        logger.info("The last image");
                        images.clear();
                        action = action.addFile(
                                WolframAlphaCommandUtils.imageToBytes(WolframAlphaCommandUtils.combineImages(images,
                                        resultHeight + image.getHeight())),
                                "result%d.png".formatted(++filesAttached));
                        embeds.add(embedBuilder
                            .setImage("attachment://result%d.png".formatted(filesAttached))
                            .build());
                    }
                    resultHeight += readImage.getHeight();
                    images.add(readImage);
                } catch (IOException e) {
                    logger.error("Failed to read image {} from the WolframAlpha response", image,
                            e);
                    return "Unable to generate message based on the WolframAlpha response";
                }
            }
        }
        action.setEmbeds(embeds);
        return "";
    }

    private int getWidth(String header) {
        BufferedImage image = new BufferedImage(100, 100, 6);
        Graphics g = image.getGraphics();
        g.setFont(WOLFRAM_ALPHA_FONT);
        return g.getFontMetrics().stringWidth(header);
    }
}
