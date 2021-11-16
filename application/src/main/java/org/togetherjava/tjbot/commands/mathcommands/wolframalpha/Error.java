package org.togetherjava.tjbot.commands.mathcommands.wolframalpha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("error")
@JsonIgnoreProperties(ignoreUnknown = true)
final class Error {
    private ErrorCode code;
    @JsonProperty("msg")
    private ErrorMessage message;

    public ErrorCode getCode() {
        return code;
    }

    @SuppressWarnings("unused")
    public void setCode(ErrorCode code) {
        this.code = code;
    }

    public ErrorMessage getMessage() {
        return message;
    }

    @SuppressWarnings("unused")
    public void setMessage(ErrorMessage message) {
        this.message = message;
    }


}
