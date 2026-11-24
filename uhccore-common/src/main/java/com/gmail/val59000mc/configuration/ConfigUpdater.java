package com.gmail.val59000mc.configuration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import com.gmail.val59000mc.configuration.YamlOutline.Node;
import com.gmail.val59000mc.configuration.YamlOutline.UnsupportedYamlException;

/**
 * Keeps the files in the plugin folder in step with the ones inside the jar without touching what the admin wrote.
 * <p>
 * Loading and saving through Bukkit would be the obvious way, but before 1.18.1 it throws every comment away and
 * on any version it rewrites quotes, indentation and blank lines. So the update works on the text: whatever the file
 * already has is left byte for byte as it is, and each key that exists in the jar's copy but not in the file is
 * pasted in, comment included, next to the keys that surround it in the jar's copy.
 */
final class ConfigUpdater {

	private static final Logger LOGGER = Logger.getLogger(ConfigUpdater.class.getCanonicalName());

	// Sections where the default file only gives examples (one entry per block, mob or ore). The admin is expected
	// to delete or replace those entries, so a missing entry is not a missing option and must not come back.
	private static final Set<String> USER_ENTRY_SECTIONS = Collections.unmodifiableSet(
			new HashSet<>(Arrays.asList("loots", "veins")));

	private ConfigUpdater() {
	}

	static final class Result {
		final String text;
		final List<String> added;
		final List<String> commented;

		private Result(String text, List<String> added, List<String> commented) {
			this.text = text;
			this.added = added;
			this.commented = commented;
		}
	}

	/**
	 * Brings {@code userText} up to date with {@code templateText} in two passes, and only ever by adding lines:
	 * <ol>
	 * <li>The keys the template has and the file lacks are pasted in, with their comments.</li>
	 * <li>Keys the file has but that carry no comment at all get the template's comment. Old versions saved whole
	 * files through Bukkit and dropped the comments of everything they wrote, so those keys are common.
	 * A key with any comment of the admin's above it, or on its own line, is considered documented and is left alone,
	 * even if the template says something else.</li>
	 * </ol>
	 * Existing keys, values, comments, order and formatting are untouched, and keys the template doesn't know about stay
	 * where they are. A key whose parent exists but holds a plain value is skipped: that is how an old {@code sound: true}
	 * stays valid next to the new {@code sound:} section.
	 *
	 * @throws UnsupportedYamlException if either text uses YAML this class doesn't handle, or if the result doesn't
	 * read back with every old value intact and every old line still in place. The caller must then leave the file as it is.
	 */
	static Result update(String userText, String templateText) throws UnsupportedYamlException {
		final TextFile user = TextFile.of(userText);
		final TextFile template = TextFile.of(templateText);
		final YamlOutline templateOutline = YamlOutline.parse(template.lines);

		final List<String> added = addMissingKeys(user, template, templateOutline);
		final List<String> commented = addMissingComments(user, template, templateOutline);

		final String result = user.join();
		if (!added.isEmpty() || !commented.isEmpty()) {
			verify(userText, result);
		}
		return new Result(result, Collections.unmodifiableList(added), Collections.unmodifiableList(commented));
	}

	private static List<String> addMissingKeys(TextFile user, TextFile template, YamlOutline templateOutline) throws UnsupportedYamlException {
		final List<String> added = new ArrayList<>();

		boolean converged = false;
		// Every pass inserts one key, so the template size is a hard ceiling
		for (int pass = 0; pass <= templateOutline.getNodes().size(); pass++) {
			final YamlOutline userOutline = YamlOutline.parse(user.lines);
			final Node missing = findMissing(templateOutline, userOutline);
			if (missing == null) {
				converged = true;
				break;
			}
			insert(user, userOutline, template, templateOutline, missing);
			added.add(String.join(".", missing.path));
		}
		if (!converged) {
			throw new UnsupportedYamlException("the update did not settle");
		}
		return added;
	}

	private static List<String> addMissingComments(TextFile user, TextFile template, YamlOutline templateOutline) throws UnsupportedYamlException {
		final List<String> commented = new ArrayList<>();
		final Set<List<String>> done = new HashSet<>();

		// One comment per pass: every insertion shifts the line numbers, so the file is mapped again each time
		final int ceiling = YamlOutline.parse(user.lines).getNodes().size() + 1;
		for (int pass = 0; pass <= ceiling; pass++) {
			final YamlOutline userOutline = YamlOutline.parse(user.lines);
			Node target = null;
			Node source = null;
			for (Node node : userOutline.getNodes()) {
				if (done.contains(node.path) || hasUserEntryAncestor(node) || isCommented(user, node)) {
					continue;
				}
				final Node candidate = templateOutline.get(node.path);
				if (candidate != null && candidate.commentStart < candidate.keyLine) {
					target = node;
					source = candidate;
					break;
				}
			}
			if (target == null) {
				return commented;
			}

			done.add(target.path);
			insertComment(user, userOutline, template, target, source);
			commented.add(String.join(".", target.path));
		}
		throw new UnsupportedYamlException("the comment update did not settle");
	}

	/**
	 * A key counts as documented if any comment sits between the previous key and it, even one separated by a
	 * blank line (it may be a section banner the admin wrote), or if its own line ends with a comment.
	 */
	private static boolean isCommented(TextFile user, Node node) {
		if (node.inlineComment) {
			return true;
		}
		for (int i = node.leadStart; i < node.keyLine; i++) {
			if (user.lines.get(i).trim().startsWith("#")) {
				return true;
			}
		}
		return false;
	}

	private static void insertComment(TextFile user, YamlOutline userOutline, TextFile template, Node target, Node source) {
		final List<String> block = new ArrayList<>(template.lines.subList(source.commentStart, source.keyLine));
		shiftIndent(block, target.indent - source.indent);

		// Keep the template's blank line above the comment when it makes sense: not when the file is already spaced
		// there, and not right under the parent key, where the template puts the first child's comment too
		final boolean spacedInTemplate = source.commentStart > 0 && template.lines.get(source.commentStart - 1).trim().isEmpty();
		final String above = target.keyLine > 0 ? user.lines.get(target.keyLine - 1) : "";
		final Node parent = target.path.size() > 1 ? userOutline.get(target.parentPath()) : null;
		final boolean underParent = parent != null && parent.keyLine == target.keyLine - 1;
		if (spacedInTemplate && !above.trim().isEmpty() && !underParent) {
			block.add(0, "");
		}
		user.lines.addAll(target.keyLine, block);
	}

	/**
	 * @return the first template key, in file order, that is missing from the user's file and can be added
	 */
	private static Node findMissing(YamlOutline template, YamlOutline user) {
		for (Node node : template.getNodes()) {
			if (user.get(node.path) != null) {
				continue;
			}
			if (USER_ENTRY_SECTIONS.contains(node.name()) || hasUserEntryAncestor(node)) {
				continue;
			}

			final List<String> parentPath = node.parentPath();
			if (!parentPath.isEmpty()) {
				final Node parent = user.get(parentPath);
				// No parent: it is missing too and was either added already or can't be. A parent that holds a
				// value (or nothing at all) is the admin's choice and stays as written.
				if (parent == null || !parent.hasChildren) {
					continue;
				}
			}
			return node;
		}
		return null;
	}

	private static boolean hasUserEntryAncestor(Node node) {
		final List<String> parents = node.parentPath();
		for (String name : parents) {
			if (USER_ENTRY_SECTIONS.contains(name)) {
				return true;
			}
		}
		return false;
	}

	private static void insert(TextFile user, YamlOutline userOutline, TextFile template, YamlOutline templateOutline, Node missing) {
		final List<String> parentPath = missing.parentPath();

		// Neighbours are looked up in the template's order, so the new key lands where it sits in the default file
		Node previous = null;
		Node next = null;
		boolean passedMissing = false;
		for (Node sibling : templateOutline.getNodes()) {
			if (!sibling.parentPath().equals(parentPath)) {
				continue;
			}
			if (sibling == missing) {
				passedMissing = true;
				continue;
			}
			final Node existing = userOutline.get(sibling.path);
			if (existing == null) {
				continue;
			}
			if (!passedMissing) {
				previous = existing;
			} else if (next == null) {
				next = existing;
			}
		}

		final int insertAt;
		final int indent;
		if (previous != null) {
			insertAt = previous.endLine + 1;
			indent = previous.indent;
		} else if (next != null) {
			insertAt = next.leadStart;
			indent = next.indent;
		} else if (parentPath.isEmpty()) {
			insertAt = endOfContent(user.lines);
			indent = 0;
		} else {
			final Node parent = userOutline.get(parentPath);
			insertAt = parent.endLine + 1;
			indent = firstChildIndent(userOutline, parent);
		}

		final List<String> block = new ArrayList<>(template.lines.subList(missing.attachedStart, missing.endLine + 1));
		shiftIndent(block, indent - missing.indent);
		// Top level keys are always separated by a blank line in the default files
		if (indent == 0 && !block.get(0).trim().isEmpty() && insertAt > 0) {
			block.add(0, "");
		}
		user.lines.addAll(insertAt, block);
	}

	private static int endOfContent(List<String> lines) {
		int end = lines.size();
		// A file that ends with a newline splits into a last empty element, new lines go before it
		if (end > 0 && lines.get(end - 1).isEmpty()) {
			end--;
		}
		return end;
	}

	private static int firstChildIndent(YamlOutline outline, Node parent) {
		for (Node node : outline.getNodes()) {
			if (node.parentPath().equals(parent.path)) {
				return node.indent;
			}
		}
		return parent.indent + 2;
	}

	private static void shiftIndent(List<String> lines, int delta) {
		if (delta == 0) {
			return;
		}
		for (int i = 0; i < lines.size(); i++) {
			final String line = lines.get(i);
			if (line.trim().isEmpty()) {
				continue;
			}
			if (delta > 0) {
				lines.set(i, spaces(delta) + line);
			} else {
				int remove = 0;
				while (remove < -delta && remove < line.length() && line.charAt(remove) == ' ') {
					remove++;
				}
				lines.set(i, line.substring(remove));
			}
		}
	}

	private static String spaces(int count) {
		final StringBuilder builder = new StringBuilder(count);
		for (int i = 0; i < count; i++) {
			builder.append(' ');
		}
		return builder.toString();
	}

	/**
	 * Reads both texts back with Bukkit's own parser: the new one has to be valid YAML and every value of the old
	 * one has to come out the same. This is the safety net for everything the outline gets wrong.
	 */
	private static void verify(String before, String after) throws UnsupportedYamlException {
		final Map<String, Object> oldValues;
		final Map<String, Object> newValues;
		try {
			oldValues = readValues(before);
			newValues = readValues(after);
		} catch (InvalidConfigurationException e) {
			throw new UnsupportedYamlException("the result is not valid YAML: " + e.getMessage());
		}

		for (Map.Entry<String, Object> entry : oldValues.entrySet()) {
			if (!newValues.containsKey(entry.getKey()) || !entry.getValue().equals(newValues.get(entry.getKey()))) {
				throw new UnsupportedYamlException("the value of " + entry.getKey() + " would change");
			}
		}

		// Values alone would let a rewritten comment or a moved line through
		final List<String> oldLines = TextFile.of(before).lines;
		final List<String> newLines = TextFile.of(after).lines;
		int next = 0;
		for (String line : newLines) {
			if (next < oldLines.size() && oldLines.get(next).equals(line)) {
				next++;
			}
		}
		if (next != oldLines.size()) {
			throw new UnsupportedYamlException("line " + (next + 1) + " of the file would change");
		}
	}

	private static Map<String, Object> readValues(String text) throws InvalidConfigurationException {
		final YamlConfiguration yaml = new YamlConfiguration();
		yaml.loadFromString(text);
		final Map<String, Object> values = new HashMap<>();
		for (String key : yaml.getKeys(true)) {
			final Object value = yaml.get(key);
			if (!(value instanceof ConfigurationSection)) {
				// A key with nothing after it reads as null, keep it so a removed key is noticed
				values.put(key, value == null ? Collections.emptyList() : value);
			}
		}
		return values;
	}

	/**
	 * Puts the comments of {@code oldText} (and, for keys it never had, the ones of the template) back on a
	 * freshly dumped YAML text. Used for the saves that have to rewrite the whole file, like adding a custom craft.
	 * <p>
	 * Matching is by the full path of each key, so two sections that both have an {@code enable:} can't swap comments.
	 *
	 * @return the dump with comments, or the dump untouched if one of the texts can't be mapped
	 */
	static String restoreComments(String dumpedText, String oldText, String templateText) {
		try {
			final TextFile dumped = TextFile.of(dumpedText);
			final List<String> bare = new ArrayList<>();
			for (String line : dumped.lines) {
				final String trimmed = line.trim();
				// Bukkit 1.18.1+ already wrote comments and blank lines in here, ours replace them
				if (!trimmed.isEmpty() && trimmed.charAt(0) != '#') {
					bare.add(line);
				}
			}
			final YamlOutline outline = YamlOutline.parse(bare);
			final TextFile old = oldText == null ? null : TextFile.of(oldText);
			final YamlOutline oldOutline = old == null ? null : tryParse(old.lines);
			final TextFile template = templateText == null ? null : TextFile.of(templateText);
			final YamlOutline templateOutline = template == null ? null : tryParse(template.lines);

			final Map<Integer, Node> nodeByLine = new HashMap<>();
			for (Node node : outline.getNodes()) {
				nodeByLine.put(node.keyLine, node);
			}

			final List<String> result = new ArrayList<>();
			for (int i = 0; i < bare.size(); i++) {
				final Node node = nodeByLine.get(i);
				if (node != null) {
					final Node known = oldOutline == null ? null : oldOutline.get(node.path);
					final List<String> comments = new ArrayList<>();
					if (known != null) {
						comments.addAll(old.lines.subList(known.leadStart, known.keyLine));
						shiftIndent(comments, node.indent - known.indent);
					} else if (templateOutline != null && templateOutline.get(node.path) != null) {
						final Node fromTemplate = templateOutline.get(node.path);
						comments.addAll(template.lines.subList(fromTemplate.attachedStart, fromTemplate.keyLine));
						shiftIndent(comments, node.indent - fromTemplate.indent);
					}
					result.addAll(comments);
				}
				result.add(bare.get(i));
			}

			if (oldOutline != null && !oldOutline.getNodes().isEmpty()) {
				result.addAll(old.lines.subList(oldOutline.getLastContentLine() + 1, old.lines.size()));
				// The tail already ends with the empty element of the final newline
				if (!result.get(result.size() - 1).isEmpty()) {
					result.add("");
				}
			} else {
				result.add("");
			}

			final TextFile output = new TextFile(old == null ? "" : old.bom, old == null ? "\n" : old.eol, result);
			return output.join();
		} catch (UnsupportedYamlException e) {
			LOGGER.fine("Saving without restoring comments: " + e.getMessage());
			return dumpedText;
		}
	}

	private static YamlOutline tryParse(List<String> lines) {
		try {
			return YamlOutline.parse(lines);
		} catch (UnsupportedYamlException e) {
			return null;
		}
	}

	/**
	 * Adds the missing keys to the file, logging what was added. Does nothing, and says why, if the file can't be
	 * handled safely: the options then run on their defaults until the admin adds them.
	 *
	 * @return true if the file was changed
	 */
	static boolean updateFile(File file, String templateText) throws IOException {
		final String current = readText(file);
		final Result result;
		try {
			result = update(current, templateText);
		} catch (UnsupportedYamlException e) {
			LOGGER.warning("Couldn't update " + file.getName() + " (" + e.getMessage()
					+ "). The file was left as it is and the missing options use their default values");
			return false;
		}

		if (result.added.isEmpty() && result.commented.isEmpty()) {
			return false;
		}

		writeText(file, result.text);
		if (!result.added.isEmpty()) {
			LOGGER.info("Added " + result.added.size() + " new option(s) to " + file.getName() + ": " + String.join(", ", result.added));
		}
		if (!result.commented.isEmpty()) {
			LOGGER.info("Added the default comment to " + result.commented.size() + " option(s) of " + file.getName() + " that had none");
		}
		return true;
	}

	static String readText(File file) throws IOException {
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}

	static String readText(InputStream stream) throws IOException {
		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		final byte[] chunk = new byte[4096];
		int read;
		while ((read = stream.read(chunk)) != -1) {
			buffer.write(chunk, 0, read);
		}
		return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
	}

	static void writeText(File file, String text) throws IOException {
		// Written next to the file and moved over it, a crash halfway can't leave a half written config behind
		final File temp = new File(file.getParentFile(), file.getName() + ".tmp");
		Files.write(temp.toPath(), text.getBytes(StandardCharsets.UTF_8));
		Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * A file split in lines, remembering the byte order mark and the line ending it came with so that
	 * joining it again gives back the same bytes.
	 */
	private static final class TextFile {
		private final String bom;
		private final String eol;
		private final List<String> lines;

		private TextFile(String bom, String eol, List<String> lines) {
			this.bom = bom;
			this.eol = eol;
			this.lines = lines;
		}

		static TextFile of(String text) throws UnsupportedYamlException {
			final String bom = text.startsWith("﻿") ? "﻿" : "";
			final String body = bom.isEmpty() ? text : text.substring(1);
			final boolean crlf = body.contains("\r\n");
			final String unified = body.replace("\r\n", "\n");

			if (unified.indexOf('\r') >= 0) {
				throw new UnsupportedYamlException("stray carriage returns");
			}
			if (crlf && body.replace("\r\n", "").indexOf('\n') >= 0) {
				throw new UnsupportedYamlException("mixed line endings");
			}

			// -1 keeps the empty element after a final newline, so join() gives the text back exactly
			return new TextFile(bom, crlf ? "\r\n" : "\n", new ArrayList<>(Arrays.asList(unified.split("\n", -1))));
		}

		String join() {
			return bom + String.join(eol, lines);
		}
	}

}
