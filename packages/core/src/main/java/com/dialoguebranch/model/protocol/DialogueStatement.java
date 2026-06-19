/*
 *
 *                 Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 *
 *     This material is part of the Dialogue Branch Platform, and is covered by the MIT License
 *                                        as outlined below.
 *
 *                                            ----------
 *
 * Copyright (c) 2023-2026 Dialogue Branch (www.dialoguebranch.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.dialoguebranch.model.protocol;

import com.dialoguebranch.model.command.InputCommand;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import nl.rrd.utils.exception.ParseException;
import nl.rrd.utils.json.JsonMapper;
import com.dialoguebranch.model.NodeBody;
import com.dialoguebranch.model.command.ActionCommand;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is used for dialogue statements that are sent to the client in the web service
 * protocol. It mirrors the statement segments in a {@link NodeBody}. The main difference is that
 * any variables have been resolved and commands such as "if", "set" and "random" have already been
 * executed. There are three types of segments:
 *
 * <ul>
 *   <li>{@link TextSegment TextSegment}: Corresponds to a {@link NodeBody.TextSegment} with
 *   variables resolved.</li>
 *   <li>{@link ActionSegment}: Contains a {@link DialogueAction DialogueAction}, which corresponds
 *   to a {@link ActionCommand} with variables resolved. Action segments should not occur in
 *   statements that are part of a {@link ReplyMessage ReplyMessage}.</li>
 *   <li>{@link InputSegment InputSegment}: Corresponds to a {@link InputCommand} with variables
 *   resolved.</li>
 * </ul>
 *
 * @author Dennis Hofs
 * @author Harm op den Akker
 */
public class DialogueStatement {

	/** Creates an empty {@link DialogueStatement} with no segments. */
	public DialogueStatement() {
	}

	private List<Segment> segments = new ArrayList<>();

	/**
	 * Returns the ordered list of segments making up this statement.
	 * @return the segment list.
	 */
	public List<Segment> getSegments() {
		return segments;
	}

	/**
	 * Sets the ordered list of segments making up this statement.
	 * @param segments the segment list.
	 */
	public void setSegments(List<Segment> segments) {
		this.segments = segments;
	}

	/**
	 * Appends a plain-text segment with the given {@code text} to this statement.
	 * @param text the text content.
	 */
	public void addTextSegment(String text) {
		TextSegment segment = new TextSegment();
		segment.setText(text);
		segments.add(segment);
	}
	
	/**
	 * Appends an input segment derived from the given {@link InputCommand} to this statement.
	 * @param inputCommand the resolved input command.
	 */
	public void addInputSegment(InputCommand inputCommand) {
		InputSegment segment = new InputSegment();
		segment.setInputType(inputCommand.getType());
		segment.setDescription(inputCommand.getDescription());
		segment.setParameters(inputCommand.getParameters());
		segments.add(segment);
	}
	
	/**
	 * Appends an action segment derived from the given {@link ActionCommand} to this statement.
	 * @param actionCommand the resolved action command.
	 */
	public void addActionSegment(ActionCommand actionCommand) {
		ActionSegment segment = new ActionSegment();
		segment.setAction(new DialogueAction(actionCommand));
		segments.add(segment);
	}

	/** Distinguishes the three kinds of segment that a {@link DialogueStatement} may contain. */
	public enum SegmentType {
		/** A plain-text segment. */
		TEXT,
		/** An input-command segment. */
		INPUT,
		/** An action-command segment. */
		ACTION
	}

	/** Base type for the three kinds of segment in a {@link DialogueStatement}. */
	@JsonDeserialize(using=SegmentDeserializer.class)
	public static abstract class Segment {
		private final SegmentType segmentType;

		/**
		 * Creates a {@link Segment} of the given type.
		 * @param segmentType the type of this segment.
		 */
		protected Segment(SegmentType segmentType) {
			this.segmentType = segmentType;
		}

		/**
		 * Returns the type of this segment.
		 * @return the segment type.
		 */
		public SegmentType getSegmentType() {
			return segmentType;
		}
	}

	/** A segment containing plain text (with all variables already resolved). */
	@JsonDeserialize(using=JsonDeserializer.None.class)
	public static class TextSegment extends Segment {
		private String text;

		/** Creates an empty {@link TextSegment}. */
		public TextSegment() {
			super(SegmentType.TEXT);
		}

		/**
		 * Returns the plain-text content of this segment.
		 * @return the text content.
		 */
		public String getText() {
			return text;
		}

		/**
		 * Sets the plain-text content of this segment.
		 * @param text the text content.
		 */
		public void setText(String text) {
			this.text = text;
		}
	}
	
	/** A segment representing an input command whose parameters have already been resolved. */
	@JsonDeserialize(using=InputSegmentDeserializer.class)
	@JsonSerialize(using=InputSegmentSerializer.class)
	public static class InputSegment extends Segment {
		private String inputType;
		private String description = null;
		private Map<String,?> parameters = new LinkedHashMap<>();

		/** Creates an empty {@link InputSegment}. */
		public InputSegment() {
			super(SegmentType.INPUT);
		}

		/**
		 * Returns the input type. This should be one of the TYPE_* constants
		 * defined in {@link InputCommand}.
		 *
		 * @return the input type
		 */
		public String getInputType() {
			return inputType;
		}

		/**
		 * Sets the input type. This should be one of the TYPE_* constants
		 * defined in {@link InputCommand}.
		 *
		 * @param inputType the input type
		 */
		public void setInputType(String inputType) {
			this.inputType = inputType;
		}

		/**
		 * Returns the description of this input command. For example a client can
		 * use this in input validation messages ("You did not fill in [your
		 * name]."). The description is optional and may be null.
		 *
		 * @return the description or null
		 */
		public String getDescription() {
			return description;
		}

		/**
		 * Sets the description of this input command. For example a client can use
		 * this in input validation messages ("You did not fill in [your name].").
		 * The description is optional and may be null.
		 *
		 * @param description the description or null
		 */
		public void setDescription(String description) {
			this.description = description;
		}

		/**
		 * Returns the parameters. This is a map from parameter names to values.
		 * A value can be any JSON type. Any variables in parameter values have
		 * already been resolved.
		 *
		 * @return the parameters
		 */
		public Map<String, ?> getParameters() {
			return parameters;
		}

		/**
		 * Sets the parameters. This is a map from parameter names to values. A
		 * value can be any JSON type. Any variables in parameter values have
		 * already been resolved.
		 *
		 * @param parameters the parameters
		 */
		public void setParameters(Map<String, ?> parameters) {
			this.parameters = parameters;
		}
	}
	
	/** A segment containing a resolved {@link DialogueAction}. */
	@JsonDeserialize(using=JsonDeserializer.None.class)
	public static class ActionSegment extends Segment {
		private DialogueAction action;

		/** Creates an empty {@link ActionSegment}. */
		public ActionSegment() {
			super(SegmentType.ACTION);
		}

		/**
		 * Returns the {@link DialogueAction} contained in this segment.
		 * @return the dialogue action.
		 */
		public DialogueAction getAction() {
			return action;
		}

		/**
		 * Sets the {@link DialogueAction} for this segment.
		 * @param action the dialogue action.
		 */
		public void setAction(DialogueAction action) {
			this.action = action;
		}
	}

	/**
	 * Jackson deserializer that dispatches to the correct {@link Segment} subclass based on the
	 * {@code segmentType} field.
	 */
	public static class SegmentDeserializer extends JsonDeserializer<Segment> {

		/** Creates a new {@link SegmentDeserializer}. */
		public SegmentDeserializer() {}

		@Override
		public Segment deserialize(JsonParser p, DeserializationContext ctxt)
				throws IOException, JsonProcessingException {
			Map<?,?> map = p.readValueAs(Map.class);
			if (!map.containsKey("segmentType")) {
				throw new JsonParseException(p,
						"Property \"segmentType\" not found");
			}
			Object typeObj = map.remove("segmentType");
			if (!(typeObj instanceof String)) {
				throw new JsonParseException(p,
						"Invalid value of property \"segmentType\": " +
						typeObj);
			}
			String typeStr = (String)typeObj;
			SegmentType type;
			try {
				type = SegmentType.valueOf(typeStr);
			} catch (IllegalArgumentException ex) {
				throw new JsonParseException(p,
						"Invalid value of property \"segmentType\": " +
						typeStr);
			}
			ObjectMapper mapper = new ObjectMapper();
			switch (type) {
			case TEXT:
				return mapper.convertValue(map, TextSegment.class);
			case INPUT:
				return mapper.convertValue(map, InputSegment.class);
			case ACTION:
				return mapper.convertValue(map, ActionSegment.class);
			default:
				throw new JsonParseException(p, "Unsupported segment type: " +
						type);
			}
		}
	}

	/** Jackson serializer that flattens {@link InputSegment} parameters into the JSON object. */
	public static class InputSegmentSerializer extends
			JsonSerializer<InputSegment> {

		/** Creates a new {@link InputSegmentSerializer}. */
		public InputSegmentSerializer() {}

		@Override
		public void serialize(InputSegment value, JsonGenerator gen,
				SerializerProvider serializers) throws IOException {
			Map<String,Object> obj = new LinkedHashMap<>();
			obj.put("segmentType", value.getSegmentType());
			obj.put("inputType", value.getInputType());
			if (value.getDescription() != null)
				obj.put("description", value.getDescription());
			for (String param : value.getParameters().keySet()) {
				obj.put(param, value.getParameters().get(param));
			}
			gen.writeObject(obj);
		}
	}

	/** Jackson deserializer that reconstructs an {@link InputSegment} from a flat JSON object. */
	public static class InputSegmentDeserializer extends
			JsonDeserializer<InputSegment> {

		/** Creates a new {@link InputSegmentDeserializer}. */
		public InputSegmentDeserializer() {}

		@Override
		public InputSegment deserialize(JsonParser p,
				DeserializationContext ctxt) throws IOException,
				JsonProcessingException {
			Map<?,?> rawMap = p.readValueAs(Map.class);
			Map<String, ?> map;
			try {
				map = JsonMapper.convert(rawMap,
						new TypeReference<Map<String, ?>>() {});
			} catch (ParseException ex) {
				throw new JsonParseException(p, "Object keys are not strings");
			}
			map.remove("segmentType");
			InputSegment segment = new InputSegment();
			if (!map.containsKey("inputType")) {
				throw new JsonParseException(p,
						"Property \"inputType\" not found");
			}
			Object typeObj = map.remove("inputType");
			if (!(typeObj instanceof String)) {
				throw new JsonParseException(p,
						"Invalid value of property \"inputType\": " +
						typeObj);
			}
			String typeStr = (String)typeObj;
			segment.setInputType(typeStr);
			Object descrObj = map.remove("description");
			if (descrObj != null) {
				if (!(descrObj instanceof String)) {
					throw new JsonParseException(p,
							"Invalid value of property \"description\": " +
							descrObj);
				}
				segment.setDescription((String)descrObj);
			}
			segment.setParameters(map);
			return segment;
		}
	}
}
