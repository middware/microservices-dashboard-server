
package com.pxs.dependencies.model;

import static com.pxs.dependencies.constants.Constants.DETAILS;
import static com.pxs.dependencies.constants.Constants.ID;
import static com.pxs.dependencies.constants.Constants.LANE;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Node {
	@JsonProperty(ID)
	private String id;

	@JsonProperty(DETAILS)
	private Map<String, Object> details;

	@JsonProperty(LANE)
	private Integer lane;

	@JsonProperty("linkedNodes")
	private List<Node> linkednodes;

	public void setLane(Integer lane) {
		this.lane = lane;
	}

	public Integer getLane() {
		return lane;
	}

	public void setId(java.lang.String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public List<Node> getLinkednodes() {
		return linkednodes;
	}

	public void setLinkednodes(List<Node> linkednodes) {
		this.linkednodes = linkednodes;
	}

	public Map<String, Object> getDetails() {
		return details;
	}

	public void setDetails(Map<String, Object> details) {
		this.details = details;
	}

	@Override
	public String toString() {
		return "Node{" +
				"details=" + details +
				", id='" + id + '\'' +
				", lane=" + lane +
				", linkednodes=" + linkednodes +
				'}';
	}
}