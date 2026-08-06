package com.newportai.liveavatar.channel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data payload for the {@code scene.resourceTransition} event.
 *
 * <p>The platform sends this event to the agent when the renderer is about to
 * switch from one configured video resource to another. The event is delivered
 * over the existing agent WebSocket connection and is scoped by the surrounding
 * {@link Message#getSessionId()}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceTransitionData {

    /**
     * ID of the video resource that has just finished or is being switched away from.
     *
     * <p>This field is required. It is the stable business resource ID, not a URL,
     * file path, or display name.</p>
     */
    @JsonProperty("previousResourceId")
    private String previousResourceId;

    /**
     * ID of the video resource that the renderer is about to switch to.
     *
     * <p>This field is required. It is the stable business resource ID, not a URL,
     * file path, or display name.</p>
     */
    @JsonProperty("nextResourceId")
    private String nextResourceId;

    /**
     * Optional human-readable context supplied by the platform.
     *
     * <p>The SDK does not parse this field. Application logic should make
     * decisions from {@link #getPreviousResourceId()} and
     * {@link #getNextResourceId()} instead.</p>
     */
    @JsonProperty("message")
    private String message;

    public ResourceTransitionData() {
    }

    public ResourceTransitionData(String previousResourceId, String nextResourceId, String message) {
        this.previousResourceId = previousResourceId;
        this.nextResourceId = nextResourceId;
        this.message = message;
    }

    public String getPreviousResourceId() {
        return previousResourceId;
    }

    public void setPreviousResourceId(String previousResourceId) {
        this.previousResourceId = previousResourceId;
    }

    public String getNextResourceId() {
        return nextResourceId;
    }

    public void setNextResourceId(String nextResourceId) {
        this.nextResourceId = nextResourceId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean hasRequiredResourceIds() {
        return previousResourceId != null && !previousResourceId.trim().isEmpty()
                && nextResourceId != null && !nextResourceId.trim().isEmpty();
    }
}
