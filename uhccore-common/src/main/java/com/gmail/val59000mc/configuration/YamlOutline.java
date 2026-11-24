package com.gmail.val59000mc.configuration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A line based map of a YAML file: which line holds which key, how far each key's block reaches and
 * which comments sit right above it.
 * <p>
 * Bukkit only keeps comments when saving from 1.18.1 on, and the plugin runs down to 1.8.8, so the
 * config files are edited as text instead of going through a load and save. That needs to know where a
 * key starts and ends without a YAML library, and this does it for the plain block style the plugin
 * configs are written in. Anything fancier (anchors, tabs, multi-line scalars, duplicate keys...) makes
 * it throw {@link UnsupportedYamlException}, and the caller leaves the file alone: refusing is cheaper
 * than corrupting somebody's config.
 */
final class YamlOutline {

	static final class UnsupportedYamlException extends Exception {

		UnsupportedYamlException(String message) {
			super(message);
		}

	}

	static final class Node {
		final List<String> path;
		final int indent;
		final int keyLine;
		// First line of everything between the previous key and this one (blank lines and comments)
		int leadStart;
		// First line of the comment block stuck to the key, plus the blank line above it if there is one
		int attachedStart;
		// First comment line of that block, equal to keyLine when the key has no comment above it
		int commentStart;
		// A comment written after the value on the key's own line
		boolean inlineComment;
		// Last line that belongs to this key, children and list items included
		int endLine;
		boolean hasValue;
		boolean hasChildren;

		private Node(List<String> path, int indent, int keyLine) {
			this.path = path;
			this.indent = indent;
			this.keyLine = keyLine;
			this.endLine = keyLine;
		}

		String name() {
			return path.get(path.size() - 1);
		}

		List<String> parentPath() {
			return path.subList(0, path.size() - 1);
		}
	}

	private final List<String> lines;
	private final List<Node> nodes = new ArrayList<>();
	private final Map<List<String>, Node> byPath = new HashMap<>();

	private YamlOutline(List<String> lines) {
		this.lines = lines;
	}

	static YamlOutline parse(List<String> lines) throws UnsupportedYamlException {
		final YamlOutline outline = new YamlOutline(lines);
		outline.scan();
		return outline;
	}

	List<Node> getNodes() {
		return Collections.unmodifiableList(nodes);
	}

	Node get(List<String> path) {
		return byPath.get(path);
	}

	List<String> getLines() {
		return lines;
	}

	/**
	 * @return the last line that holds a key, a list item or part of a value, or -1 if the file has none
	 */
	int getLastContentLine() {
		int last = -1;
		for (Node node : nodes) {
			last = Math.max(last, node.endLine);
		}
		return last;
	}

	private void scan() throws UnsupportedYamlException {
		final Deque<Node> stack = new ArrayDeque<>();
		int lastContent = -1;
		// Indent of the dashes of the list we are inside of, -1 when not in a list
		int listIndent = -1;
		// Owner of the block scalar (| or >) we are inside of
		Node blockOwner = null;
		// Quote of the multi-line quoted scalar we are inside of, 0 when not in one
		char openQuote = 0;

		for (int i = 0; i < lines.size(); i++) {
			final String line = lines.get(i);
			final String trimmed = line.trim();
			final int indent = countIndent(line);

			if (openQuote != 0) {
				// The long strings Bukkit dumps wrap over several lines, and any of them can look like a comment or a key
				lastContent = i;
				extend(stack, i);
				if (findClosingQuote(openQuote + line, 0) >= 0) {
					openQuote = 0;
				}
				continue;
			}

			if (blockOwner != null) {
				// Literal blocks can hold anything, even lines that look like comments or keys
				if (trimmed.isEmpty() || indent > blockOwner.indent) {
					if (!trimmed.isEmpty()) {
						lastContent = i;
						extend(stack, i);
					}
					continue;
				}
				blockOwner = null;
			}

			if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
				continue;
			}
			if (line.charAt(indent) == '\t') {
				throw new UnsupportedYamlException("tab indentation on line " + (i + 1));
			}
			if (trimmed.equals("---") || trimmed.equals("...") || trimmed.startsWith("--- ")) {
				throw new UnsupportedYamlException("document marker on line " + (i + 1));
			}

			final boolean dash = trimmed.equals("-") || trimmed.startsWith("- ");

			if (listIndent >= 0) {
				if (indent > listIndent || (indent == listIndent && dash)) {
					openQuote = checkValue(dash ? trimmed.substring(1).trim() : trimmed, i);
					lastContent = i;
					extend(stack, i);
					continue;
				}
				listIndent = -1;
			}

			if (dash) {
				final Node owner = stack.peek();
				if (owner == null || indent < owner.indent || owner.hasValue || owner.hasChildren) {
					throw new UnsupportedYamlException("list item out of place on line " + (i + 1));
				}
				openQuote = checkValue(trimmed.substring(1).trim(), i);
				owner.hasValue = true;
				listIndent = indent;
				lastContent = i;
				extend(stack, i);
				continue;
			}

			final Node top = stack.peek();
			if (top != null && top.hasValue && indent > top.indent && !containsKeyColon(trimmed)) {
				// Long plain strings wrap too, the next lines are more of the same value
				lastContent = i;
				extend(stack, i);
				continue;
			}

			final KeyLine key = KeyLine.parse(trimmed, i);

			while (!stack.isEmpty() && stack.peek().indent >= indent) {
				stack.pop();
			}
			final Node parent = stack.peek();
			if (parent != null && parent.hasValue) {
				throw new UnsupportedYamlException("key below a value on line " + (i + 1));
			}

			final List<String> path = new ArrayList<>();
			if (parent != null) {
				parent.hasChildren = true;
				path.addAll(parent.path);
			}
			path.add(key.name);
			final List<String> frozenPath = Collections.unmodifiableList(path);
			if (byPath.containsKey(frozenPath)) {
				throw new UnsupportedYamlException("duplicate key " + frozenPath + " on line " + (i + 1));
			}

			final Node node = new Node(frozenPath, indent, i);
			node.leadStart = findLeadStart(lastContent, i);
			node.attachedStart = findAttachedStart(lastContent, i);
			node.commentStart = node.attachedStart;
			while (node.commentStart < i && lines.get(node.commentStart).trim().isEmpty()) {
				node.commentStart++;
			}
			node.inlineComment = key.inlineComment;
			node.hasValue = key.hasValue;
			nodes.add(node);
			byPath.put(frozenPath, node);
			stack.push(node);
			lastContent = i;
			extend(stack, i);

			if (key.blockScalar) {
				blockOwner = node;
			}
			openQuote = key.openQuote;
		}
	}

	private static void extend(Deque<Node> stack, int line) {
		for (Node node : stack) {
			node.endLine = line;
		}
	}

	private int findLeadStart(int lastContent, int keyLine) {
		int start = lastContent + 1;
		// One blank line of separation is part of the style, a pile of them is not
		while (start + 1 < keyLine && lines.get(start).trim().isEmpty() && lines.get(start + 1).trim().isEmpty()) {
			start++;
		}
		return start;
	}

	private int findAttachedStart(int lastContent, int keyLine) {
		int start = keyLine;
		while (start - 1 > lastContent && lines.get(start - 1).trim().startsWith("#")) {
			start--;
		}
		if (start - 1 > lastContent && lines.get(start - 1).trim().isEmpty()) {
			start--;
		}
		return start;
	}

	private static int countIndent(String line) {
		int indent = 0;
		while (indent < line.length() && line.charAt(indent) == ' ') {
			indent++;
		}
		return indent;
	}

	/**
	 * @return the quote of a quoted scalar that is still open at the end of the line, 0 if the value is complete
	 * @throws UnsupportedYamlException for a flow collection (a [list] or {map}) that continues on the next line
	 */
	private static char checkValue(String text, int index) throws UnsupportedYamlException {
		if (text.isEmpty()) {
			return 0;
		}
		final char first = text.charAt(0);
		if ((first == '"' || first == '\'') && findClosingQuote(text, 0) < 0) {
			return first;
		}
		if (!isClosed(text)) {
			throw new UnsupportedYamlException("value spanning several lines on line " + (index + 1));
		}
		return 0;
	}

	private static boolean containsKeyColon(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == ':' && (i + 1 == text.length() || text.charAt(i + 1) == ' ')) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A quoted scalar or a flow collection that is still open at the end of the line continues on the next one.
	 */
	static boolean isClosed(String text) {
		if (text.isEmpty()) {
			return true;
		}
		final char first = text.charAt(0);
		if (first == '"' || first == '\'') {
			return findClosingQuote(text, 0) >= 0;
		}
		if (first == '[' || first == '{') {
			int depth = 0;
			for (int i = 0; i < text.length(); i++) {
				final char c = text.charAt(i);
				if (c == '"' || c == '\'') {
					final int close = findClosingQuote(text, i);
					if (close < 0) {
						return false;
					}
					i = close;
				} else if (c == '[' || c == '{') {
					depth++;
				} else if (c == ']' || c == '}') {
					depth--;
				} else if (c == '#' && i > 0 && text.charAt(i - 1) == ' ') {
					break;
				}
			}
			return depth <= 0;
		}
		return true;
	}

	/**
	 * @return the index of the quote that closes the one at {@code open}, or -1
	 */
	private static int findClosingQuote(String text, int open) {
		final char quote = text.charAt(open);
		for (int i = open + 1; i < text.length(); i++) {
			final char c = text.charAt(i);
			if (quote == '"' && c == '\\') {
				i++;
			} else if (c == quote) {
				// '' is an escaped single quote
				if (quote == '\'' && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
					i++;
				} else {
					return i;
				}
			}
		}
		return -1;
	}

	private static final class KeyLine {
		private final String name;
		private final boolean hasValue;
		private final boolean blockScalar;
		private final char openQuote;
		private final boolean inlineComment;

		private KeyLine(String name, boolean hasValue, boolean blockScalar, char openQuote, boolean inlineComment) {
			this.name = name;
			this.hasValue = hasValue;
			this.blockScalar = blockScalar;
			this.openQuote = openQuote;
			this.inlineComment = inlineComment;
		}

		static KeyLine parse(String trimmed, int index) throws UnsupportedYamlException {
			final char first = trimmed.charAt(0);
			final String name;
			String rest;

			if (first == '"' || first == '\'') {
				final int close = findClosingQuote(trimmed, 0);
				if (close < 0) {
					throw new UnsupportedYamlException("unclosed quote on line " + (index + 1));
				}
				name = unquote(trimmed.substring(1, close), first);
				rest = trimmed.substring(close + 1).trim();
				if (!rest.startsWith(":")) {
					throw new UnsupportedYamlException("not a key on line " + (index + 1));
				}
				rest = rest.substring(1);
			} else {
				if ("[{?&*!|>%@`".indexOf(first) >= 0) {
					throw new UnsupportedYamlException("unsupported syntax on line " + (index + 1));
				}
				int colon = -1;
				for (int i = 0; i < trimmed.length(); i++) {
					if (trimmed.charAt(i) == ':' && (i + 1 == trimmed.length() || trimmed.charAt(i + 1) == ' ')) {
						colon = i;
						break;
					}
				}
				if (colon <= 0) {
					throw new UnsupportedYamlException("not a key on line " + (index + 1));
				}
				name = trimmed.substring(0, colon).trim();
				rest = trimmed.substring(colon + 1);
			}

			if (name.equals("<<")) {
				throw new UnsupportedYamlException("merge key on line " + (index + 1));
			}

			final String value = stripComment(rest.trim());
			if (!value.isEmpty() && "&*!".indexOf(value.charAt(0)) >= 0) {
				throw new UnsupportedYamlException("anchor or tag on line " + (index + 1));
			}
			final char openQuote = checkValue(value, index);
			final boolean block = value.matches("[|>][+-]?[0-9]?[+-]?");
			return new KeyLine(name, !value.isEmpty(), block, openQuote, rest.trim().length() > value.length());
		}

		private static String unquote(String raw, char quote) {
			return quote == '\'' ? raw.replace("''", "'") : raw.replace("\\\"", "\"").replace("\\\\", "\\");
		}

		private static String stripComment(String value) {
			if (value.isEmpty()) {
				return value;
			}
			final char first = value.charAt(0);
			if (first == '#') {
				return "";
			}
			int from = 0;
			if (first == '"' || first == '\'') {
				final int close = findClosingQuote(value, 0);
				if (close < 0) {
					return value;
				}
				from = close + 1;
			}
			for (int i = Math.max(from, 1); i < value.length(); i++) {
				if (value.charAt(i) == '#' && value.charAt(i - 1) == ' ') {
					return value.substring(0, i).trim();
				}
			}
			return value;
		}
	}

}
