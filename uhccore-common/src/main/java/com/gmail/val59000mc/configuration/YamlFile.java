package com.gmail.val59000mc.configuration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import com.gmail.val59000mc.UhcCore;
import com.gmail.val59000mc.configuration.options.BasicOption;
import com.gmail.val59000mc.configuration.options.Option;
import com.google.common.io.Files;
import org.apache.commons.lang.Validate;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * An extension of {@link YamlConfiguration} which saves all data in Yaml to a configuration file.
 * Note that this implementation is not synchronized.
 *
 * @author Carlos Lázaro Costa
 */
public class YamlFile extends YamlConfiguration {

	private static final Logger LOGGER = Logger.getLogger(YamlFile.class.getCanonicalName());

	/** File where data will be saved for this configuration */
	private File configFile;
	/** Used to save is a default value gets added. */
	private boolean addedDefaultValues;
	/** Name of the default copy of this file inside the jar, if it has one. */
	private String templateResource;

	/**
	 * Builds this {@link FileConfiguration} without any configuration file.
	 * <p>
	 * In order to save changes you will have to use one of these methods before:<br>
	 * 	- {@link #setConfigurationFile(File)}<br>
	 * 	- {@link #setConfigurationFile(String)}<br>
	 * Or set the file when saving changes with {@link #save(File)}
	 */
	public YamlFile() {
		addedDefaultValues = false;
	}

	/**
	 * Builds this {@link FileConfiguration} with the file specified by path.
	 * @param path location for the configuration file
	 * @throws IllegalArgumentException if path is null or is a directory.
	 * <br>Note that if <code>IllegalArgumentException</code> is thrown then this
	 * configuration file will be <b>null</b>.
	 */
	public YamlFile(String path) throws IllegalArgumentException {
		setConfigurationFile(path);
		addedDefaultValues = false;
	}

	/**
	 * Builds this {@link FileConfiguration} with a source file.
	 * @param file the configuration file
	 * @throws IllegalArgumentException if file is null or is a directory.
	 * <br>Note that if <code>IllegalArgumentException</code> is thrown then this
	 * configuration file will be <b>null</b>.
	 */
	public YamlFile(File file) throws IllegalArgumentException {
		setConfigurationFile(file);
		addedDefaultValues = false;
	}

	/**
	 * Saves this {@link FileConfiguration} to the configuration file location.
	 * <p>
	 * If the file does not exist, it will be created. If already exists, it
	 * will be overwritten. If it cannot be overwritten or created, an
	 * exception will be thrown.
	 * <p>
	 * This method will save using the system default encoding, or possibly
	 * using UTF8.
	 * <p>
	 * Note that this method will not copy the comments of original configuration file,
	 * use {@link #saveWithComments()} instead.
	 * @throws IOException if it hasn't been possible to save configuration file
	 * @see #saveWithComments()
	 */
	public void save() throws IOException {
		Validate.notNull(configFile, "This configuration file is null!");
		save(configFile);
	}

	/**
	 * Saves this {@link FileConfiguration} to the configuration file location, keeping the comments.
	 * <p>
	 * The whole file is written again, so quotes, indentation and blank lines come out the way Bukkit dumps them.
	 * The comments are put back by the path of the key they were above (and, for keys the file never had,
	 * from the copy in the jar). When all that changed is that options were added, use
	 * {@link #addMissingKeysFromTemplate()} instead, it leaves the file as the admin wrote it.
	 * <p>
	 * Comments written after a value on the same line can't be kept.
	 * @throws IOException if it hasn't been possible to save configuration file
	 * @see #save()
	 */
	public void saveWithComments() throws IOException {
		Validate.notNull(configFile, "This configuration file is null!");
		String old = configFile.exists() ? ConfigUpdater.readText(configFile) : null;
		String text = ConfigUpdater.restoreComments(saveToString(), old, readTemplate());
		ConfigUpdater.writeText(configFile, text);
	}

	/**
	 * Adds the options the jar's copy has and this file lacks, with their comments and in the jar's order, and gives the
	 * jar's comment to the options that have none. Nothing else in the file changes, and nothing is written when there is
	 * nothing to add, so it is safe to call on every start. If the file can't be edited safely it is left alone and a
	 * warning says why.
	 * <p>
	 * Files that have no copy in the jar get {@link #saveWithComments()}, but only if a default value was added.
	 * @throws IOException if it hasn't been possible to write the configuration file
	 */
	public void addMissingKeysFromTemplate() throws IOException {
		Validate.notNull(configFile, "This configuration file is null!");
		String template = readTemplate();
		if (template == null) {
			if (addedDefaultValues) {
				saveWithComments();
			}
			return;
		}
		ConfigUpdater.updateFile(configFile, template);
	}

	/**
	 * Sets the file inside the jar that acts as the default copy: the source of missing options and of their comments.
	 */
	public void setTemplateResource(String resourceName) {
		templateResource = resourceName;
	}

	private String readTemplate() {
		UhcCore plugin = UhcCore.getPlugin();
		if (templateResource == null || plugin == null) {
			return null;
		}
		try (InputStream stream = plugin.getResource(templateResource)) {
			return stream == null ? null : ConfigUpdater.readText(stream);
		} catch (IOException ex) {
			LOGGER.warning("Couldn't read " + templateResource + " from the jar, no comments will be restored: " + ex.getMessage());
			return null;
		}
	}

	/**
	 * Copy this configuration file to another path, without deleting configuration file.
	 * If there is already a file on the other path it will be overwritten.
	 * @param path the location of the new file, including name (mustn't be a directory)
	 * @return the new copied file
	 * @throws FileNotFoundException if configuration file is not found as source to copy
	 * @throws IllegalArgumentException if path is a directory or it is null
	 * @throws IOException if there I/O error occurs copying file
	 */
	public File copyTo(final String path) throws FileNotFoundException, IllegalArgumentException, IOException {
		Validate.notNull(path, "Path cannot be null.");
		File copy = new File(path);
		copyTo(copy);
		return copy;
	}

	/**
	 * Copy this configuration file to another file, without deleting configuration file.
	 * @param file destination file (mustn't be a directory)
	 * @throws FileNotFoundException if configuration file is not found as source to copy
	 * @throws IllegalArgumentException if path is a directory or it is null
	 * @throws IOException if there I/O error occurs copying file
	 */
	public void copyTo(File file) throws FileNotFoundException, IllegalArgumentException, IOException {
		Validate.notNull(configFile, "This configuration file is null!");
		if (!configFile.exists())
			throw new FileNotFoundException(configFile.getName() + " is not found in " + configFile.getAbsolutePath());
		else {
			if (!file.isDirectory())
				Files.copy(configFile, file);
			else
				throw new IllegalArgumentException(file.getAbsolutePath() + " is a directory!");
		}
	}

	/**
	 * Loads this {@link YamlFile} from the specified location.
	 * <p>
	 * All the values contained within this configuration will be removed,
	 * leaving only settings and defaults, and the new values will be loaded
	 * from the given file.
	 * <p>
	 * If the file cannot be loaded for any reason, an exception will be
	 * thrown.
	 *
	 * @param file File to load from.
	 * @throws FileNotFoundException Thrown when the given file cannot be
	 *	 opened.
	 * @throws IOException Thrown when the given file cannot be read.
	 * @throws InvalidConfigurationException Thrown when the given file is not
	 *	 a valid Configuration.
	 * @throws IllegalArgumentException Thrown when file is null.
	 */
	@Override
	public void load(File file) throws IOException, InvalidConfigurationException {
		configFile = file;
		options().copyDefaults(true);
		options().copyHeader(true);
		super.load(file);
	}

	/**
	 * Loads configurations from this configuration file.
	 * @throws IOException if it hasn't been possible to load file
	 * @throws InvalidConfigurationException if has been an error while parsing configuration file
	 * @throws FileNotFoundException if configuration file is not found
	 */
	public void load() throws InvalidConfigurationException, IOException {
		Validate.notNull(configFile, "This configuration file is null!");
		options().copyDefaults(true);
		options().copyHeader(true);
		load(configFile);
	}

	/**
	 * Tests whether this configuration file exists.
	 * @return <code>true</code> if and only if this configuration file exists;
	 * <code>false</code> otherwise
	 */
	public boolean exists() {
		Validate.notNull(configFile, "This configuration file is null!");
		return configFile.exists();
	}

	/**
	 * Creates a new empty file atomically for this configuration file.
	 * <p>
	 * Furthermore there will be created the parent directories if there not exists.
	 *
	 * @param overwrite indicates if file must be overwritten if it already exists.
	 * Note that if overwrite is set to false and there is already a file with that path file
	 * will not be created and no exception is thrown.
	 * @throws IOException if I/O error occurs creating the configuration file
	 */
	public void createNewFile(boolean overwrite) throws IOException {
		Validate.notNull(configFile, "This configuration file is null!");
		if (overwrite || !configFile.exists()) {
			try {
				File parents = configFile.getParentFile();
				if (parents != null)
					parents.mkdirs();
				configFile.createNewFile();
			} catch (SecurityException e) {
				throw new IOException(e.getMessage(), e.getCause());
			}
		}
	}

	/**
	 * Deletes this configuration file from disk.
	 * @throws IOException if file cannot be deleted
	 */
	public void deleteFile() throws IOException {
		Validate.notNull(configFile, "This configuration file is null!");
		if (!configFile.delete())
			throw new IOException("Failed to delete " + configFile);
	}

	/**
	 * Returns the size of this configuration file.
	 * @return the size, in bytes, of this configuration file.
	 */
	public long getSize() {
		return configFile.length();
	}

	/**
	 * Returns the absolute pathname string of this configuration file.
	 * @return the absolute path where configuration file is located.
	 */
	public String getFilePath() {
		Validate.notNull(configFile, "This configuration file is null!");
		return configFile.getAbsolutePath();
	}

	/**
	 * Returns this configuration file where data is located.
	 * @return the configuration file where this {@link FileConfiguration} writes.
	 */
	public File getConfigurationFile() {
		return configFile;
	}

	/**
	 * Rebuilds this {@link FileConfiguration} with the file specified by path.
	 * @param path location for the configuration file
	 * @throws IllegalArgumentException if path is null or is a directory.
	 * <br>Note that if <code>IllegalArgumentException</code> is thrown then this
	 * configuration file will be <b>null</b>.
	 */
	public void setConfigurationFile(String path) throws IllegalArgumentException {
		Validate.notNull(path, "Path cannot be null.");
		configFile = new File(path);
		if (configFile.isDirectory()) {
			configFile = null;
			throw new IllegalArgumentException(configFile.getName() + " is a directory!");
		}
	}

	/**
	 * Rebuilds this {@link FileConfiguration} with a source file.
	 * @param file the configuration file
	 * @throws IllegalArgumentException if file is null or is a directory.
	 * <br>Note that if <code>IllegalArgumentException</code> is thrown then this
	 * configuration file will be <b>null</b>.
	 */
	public void setConfigurationFile(File file) throws IllegalArgumentException {
		Validate.notNull(file, "File cannot be null.");
		configFile = file;
		if (configFile.isDirectory()) {
			configFile = null;
			throw new IllegalArgumentException(configFile.getName() + " is a directory!");
		}
	}

	/**
	 * Removes the specified path if it exists.
	 * The entry will be removed, either a value or an entire section.
	 *
	 * @param path Path of the object to remove.
	 */
	public void remove(String path) {
		set(path, null);
	}

	public <T> T get(Option<T> option){
		return option.getValue(this);
	}

	/**
	 * Changes the value in memory only. Nothing hits the disk until something calls a save method,
	 * so a reload throws the change away.
	 */
	public <T> void set(BasicOption<T> option, T value){
		option.setValue(this, value);
	}

	/**
	 * When the no result is found the default will be returned and added to the yaml file.
	 * @param path The path of the setting.
	 * @param def The default value.
	 * @return The value of the setting.
	 * @author Mezy
	 */
	@Override
	public Object get(String path, Object def) {
		if (def == null){
			return super.get(path, def);
		}
		Object val = super.get(path, null);
		if (val == null){
			set(path, def);
			addedDefaultValues = true;
			return def;
		}
		return val;
	}

	public boolean addedDefaultValues(){
		return addedDefaultValues;
	}

}
