package com.github.stefvanschie.quickskript.core.file.alias;

import com.github.stefvanschie.quickskript.core.file.alias.exception.AliasFileFormatException;
import com.github.stefvanschie.quickskript.core.file.alias.exception.AliasFileResolveException;
import com.github.stefvanschie.quickskript.core.file.alias.manager.AliasFileManager;
import com.github.stefvanschie.quickskript.core.pattern.SkriptPattern;
import com.github.stefvanschie.quickskript.core.util.registry.ItemTypeRegistry;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * An alias file. This alias file contains parsed data about its contents.
 *
 * @since 0.1.0
 */
public class AliasFile {

    /**
     * All the directives in this file
     */
    private Set<AliasFileUseDirective> directives = new HashSet<>();

    /**
     * All the variations in this file
     */
    private Set<AliasFileVariation> variations = new HashSet<>();

    /**
     * All the entries in this file
     */
    private Set<AliasFileEntry> entries = new HashSet<>();

    /**
     * Resolves all possible item types from this file. This means that each possible item type that differ from each
     * other will be a separate entry. The entries are represented by patterns - each pattern permutation is the same
     * item type. The alias file manager should have loaded all alias files that this file depends on via a {@code :use}
     * directive. It is not necessary for these dependencies to be resolved, however.
     *
     * @param manager the alias file manager
     * @return all item types
     * @since 0.1.0
     */
    @NotNull
    @Contract(pure = true)
    public Collection<ItemTypeRegistry.Entry> resolveAllItemTypes(@NotNull AliasFileManager manager) {
        List<AliasFileVariation> variations = new ArrayList<>(this.variations);
        for (AliasFileUseDirective directive : directives) {
            String filePath = directive.getFilePath();
            AliasFile file = manager.getFile(filePath);

            if (file == null) {
                throw new AliasFileResolveException("Unable to find specified file named " + filePath);
            }

            variations.addAll(file.variations);
        }

        Set<ItemTypeRegistry.Entry> result = new HashSet<>();
        for (AliasFileEntry entry : entries) {
            Set<SkriptPattern> patterns = new HashSet<>();
            for (String string : variationCombinations(entry.getEntry(), variations)) {
                patterns.add(SkriptPattern.parse(string));
            }
            result.add(new ItemTypeRegistry.Entry(patterns));
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Gets a collection of all possible combinations of the specified pattern with the variations as specified
     * replaced.
     *
     * @param pattern the pattern to replace the variations in
     * @param variations the variations to replace in the pattern
     * @return alll possible combinations of variations
     * @since 0.1.0
     */
    @NotNull
    @Contract(pure = true)
    private Collection<String> variationCombinations(@NotNull String pattern,
        @NotNull List<AliasFileVariation> variations) {
        Collection<String> patterns = new HashSet<>();

        for (AliasFileVariation variation : variations) {
            String name = variation.getName();

            int index = pattern.indexOf(name);
            if (index == -1) {
                continue;
            }

            String partOne = pattern.substring(0, index);
            String partTwo = pattern.substring(index + name.length());
            for (String entry : variation.getEntries()) {
                String replaced = partOne + entry + partTwo;

                patterns.addAll(variationCombinations(replaced, variations));
            }
        }

        return patterns;
    }

    /**
     * Parses an alias file from the specified input stream
     *
     * @param inputStream the input stream that contains the contents of the file
     * @return the alias file
     * @throws IOException when something went wrong while reading the file
     * @since 0.1.0
     */
    @NotNull
    @Contract(pure = true)
    public static AliasFile parse(@NotNull InputStream inputStream) throws IOException {
        AliasFile file = new AliasFile();

        String[] lines = new String(inputStream.readAllBytes()).split("[\r\n]+");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (line.startsWith(":use ")) {
                file.directives.add(new AliasFileUseDirective(line.substring(":use".length()).trim()));
                continue;
            }

            int equalsIndex = line.indexOf('=');

            if (equalsIndex != -1) {
                boolean optional = false;
                String variationName = line.substring(0, equalsIndex).trim();

                if (variationName.startsWith("[") && variationName.endsWith("]")) {
                    optional = true;

                    variationName = variationName.substring(1, variationName.length() - 1);
                }

                StringBuilder variation = new StringBuilder(line.substring(equalsIndex + 1).trim());

                if (variation.charAt(0) != '{') {
                    throw new AliasFileFormatException(
                        "Variation declaration should have an opening curly bracket followed by it."
                    );
                }

                variation.deleteCharAt(0);

                while (!lines[i].contains("}")) {
                    variation.append(lines[i++]);
                }

                variation.deleteCharAt(variation.length() - 1);

                List<String> entries = new ArrayList<>();

                for (String entry : variation.toString().split(",")) {
                    entries.add(entry.trim());
                }

                file.variations.add(new AliasFileVariation(entries, variationName, optional));
                continue;
            }

            file.entries.add(new AliasFileEntry(line.trim()));
        }

        return file;
    }
}
