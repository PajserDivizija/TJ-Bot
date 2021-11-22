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

import java.awt.*;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WolframAlphaCommand extends SlashCommandAdapter {
    public static final Logger logger = LoggerFactory.getLogger(WolframAlphaCommand.class);
    /**
     * Starting part of a regular wolframalpha query link.
     */
    public static final String USER_ENDPOINT = "https://www.wolframalpha.com/input";
    /**
     * Maximum Embeds that can be sent in a {@link WebhookMessageUpdateAction}
     */
    static final int MAX_EMBEDS = 10;
    static final XmlMapper XML = new XmlMapper();
    static final int MAX_IMAGE_HEIGHT_PX = 400;
    /**
     * WolframAlpha text Color
     */
    static final Color WOLFRAM_ALPHA_TEXT_COLOR = Color.decode("#3C3C3C");
    /**
     * WolframAlpha Font
     */
    static final Font WOLFRAM_ALPHA_FONT = new Font("Times", Font.PLAIN, 15)
        .deriveFont(Map.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON));
    /**
     * Height of the unscaled text displayed in Font {@link #WOLFRAM_ALPHA_FONT}
     */
    static final int TEXT_HEIGHT = 30;
    private static final int HTTP_STATUS_CODE_OK = 200;
    private static final String QUERY_OPTION = "query";
    /**
     * WolframAlpha API endpoint to connect to.
     *
     * @see <a href=
     *      "https://products.wolframalpha.com/docs/WolframAlpha-API-Reference.pdf">WolframAlpha API
     *      Reference</a>.
     */
    private static final String API_ENDPOINT = "http://api.wolframalpha.com/v2/query";
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

        MessageEmbed uriEmbed = new EmbedBuilder().setTitle(query + "- Wolfram|Alpha",
                UrlBuilder.fromString(USER_ENDPOINT).addParameter("i", query).toUri().toString())
            .setDescription(
                    "Wolfram|Alpha brings expert-level knowledge and capabilities to the broadest possible range of people-spanning all professions and education levels.")
            .build();

        WebhookMessageUpdateAction<Message> action =
                event.getHook().editOriginal("").setEmbeds(uriEmbed);

        HttpRequest request = HttpRequest
            .newBuilder(UrlBuilder.fromString(API_ENDPOINT)
                .addParameter("appid", Config.getInstance().getWolframAlphaAppId())
                .addParameter("format", "image,plaintext")
                .addParameter("input", query)
                .toUri())
            .GET()
            .build();

        Optional<HttpResponse<String>> optResponse = getResponse(request, action);
        if (optResponse.isEmpty())
            return;
        HttpResponse<String> response = optResponse.get();
        Optional<QueryResult> optResult = WolframAlphaCommandUtils.parseQuery(response, action);
        if (optResult.isEmpty())
            return;
        QueryResult result = optResult.get();
        action = action.setContent("Computed in:" + result.getTiming());
        action.setContent(switch (ResultStatus.getResultStatus(result)) {

            case ERROR -> WolframAlphaCommandUtils.handleError(result);

            case NOT_SUCCESS -> WolframAlphaCommandUtils.handleMisunderstoodQuery(result);

            case SUCCESS -> "Computed in:" + result.getTiming() + "\n"
                    + (result.getTimedOutPods().isEmpty() ? ""
                            : "Some pods have timed out. Visit the URI")
                    + "\n"
                    + WolframAlphaCommandUtils.handleSuccessfulResult(result, action, uriEmbed);
        }).queue();
    }

    private @NotNull Optional<HttpResponse<String>> getResponse(@NotNull HttpRequest request,
            @NotNull WebhookMessageUpdateAction<Message> action) {
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

}
