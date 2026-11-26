package com.infinevo.core.ai.help;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.entitlement.PlatformModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * The help articles shipped in the {@code core} jar (W-114.11), loaded once at startup.
 *
 * <p>Every file under {@code help/articles/*.md} is read and checked here; a broken file stops the application, naming
 * the file and what is wrong: missing front matter or required field, an {@code id} that is not the file name, a
 * duplicate {@code id}, an unknown role code, a {@code route} that is not in {@code help/sub-routes.json} under the
 * article's {@code screen}, a {@code module} that is not {@code hrms} or {@code payroll}, or a body over {@value #MAX_BODY} characters. Whether {@code screen} and {@code action}
 * exist in the full menu is checked by {@code HelpArticlesMatchTheMenuTest} in {@code app}, where all modules are seen.
 *
 * <p>Access is never decided from {@code roles}: callers pass a {@link HelpViewer} built by {@link HelpAccess}.
 *
 * <p>From W-114.12 it also loads the role duty guides, {@code help/roles/*.md}, and the plain names of the menu's
 * screens, {@code help/screens.json}. A guide stops the application when its role code is unknown or is not the file
 * name, a line has not exactly one {@code {screen:}} marker, a screen has no name in {@code screens.json}, an
 * {@code {article:}} is not a loaded article, or its body is over {@value #MAX_GUIDE} characters.
 */
@Component
public class HelpLibrary {

    private static final Logger log = LoggerFactory.getLogger(HelpLibrary.class);

    static final String ARTICLES = "classpath*:help/articles/*.md";
    static final String SUB_ROUTES = "classpath*:help/sub-routes.json";
    static final String ROLES = "classpath*:help/roles/*.md";
    static final String SCREENS = "classpath*:help/screens.json";

    static final int MAX_TITLE = 80;
    static final int MAX_SUMMARY = 160;
    static final int MAX_BODY = 6_000;

    /** A guide fits one tool result of W-114.4 (8,000 characters) with room for its JSON. */
    static final int MAX_GUIDE = 4_000;

    /** At most this many lines go in the system prompt (spec step 6). */
    public static final int CATALOGUE_CAP = 60;

    /** The seven system role codes, {@code migration/.../core/V022__role_action.sql:74-80}. */
    static final Set<String> ROLE_CODES =
            Set.of("platform-admin", "tenant-admin", "hr", "manager", "payroll-officer", "finance", "employee");

    private static final Pattern ID = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final Pattern MARKER = Pattern.compile("\\{([a-z]+):([^}]*)}");
    private static final List<String> FIELDS =
            List.of("id", "title", "summary", "roles", "screen", "route", "action", "module", "keywords");
    private static final List<String> REQUIRED = List.of("id", "title", "summary", "roles", "screen", "keywords");

    private final Map<String, HelpArticle> articles;
    private final Map<String, String> subRoutes;
    private final Map<String, String> screenNames;
    private final Map<String, RoleGuide> guides;

    public HelpLibrary() {
        this(new PathMatchingResourcePatternResolver(), ARTICLES, SUB_ROUTES, ROLES, SCREENS);
    }

    /** For tests: articles and sub-routes from other locations, such as fixture folders; no guides. */
    HelpLibrary(ResourcePatternResolver resolver, String articlesPattern, String subRoutesPattern) {
        this(resolver, articlesPattern, subRoutesPattern, null, null);
    }

    /** For tests: everything from other locations; a {@code null} pattern loads nothing of that kind. */
    HelpLibrary(
            ResourcePatternResolver resolver,
            String articlesPattern,
            String subRoutesPattern,
            String rolesPattern,
            String screensPattern) {
        this.subRoutes = loadSubRoutes(resolver, subRoutesPattern);
        this.articles = loadArticles(resolver, articlesPattern, subRoutes);
        this.screenNames = screensPattern == null ? Map.of() : loadScreenNames(resolver, screensPattern);
        this.guides = rolesPattern == null ? Map.of() : loadGuides(resolver, rolesPattern, articles, screenNames);
        log.info("Loaded {} help articles and {} role guides", articles.size(), guides.size());
    }

    /** For tests: a library holding exactly these articles. */
    static HelpLibrary of(List<HelpArticle> list) {
        return of(list, List.of(), Map.of());
    }

    /** For tests: a library holding exactly these articles, guides and screen names. */
    static HelpLibrary of(List<HelpArticle> list, List<RoleGuide> guideList, Map<String, String> names) {
        return new HelpLibrary(list, guideList, names);
    }

    private HelpLibrary(List<HelpArticle> list, List<RoleGuide> guideList, Map<String, String> names) {
        Map<String, HelpArticle> map = new TreeMap<>();
        list.forEach(a -> map.put(a.id(), a));
        this.articles = Collections.unmodifiableMap(map);
        this.subRoutes = Map.of();
        this.screenNames = Map.copyOf(names);
        Map<String, RoleGuide> byRole = new TreeMap<>();
        guideList.forEach(g -> byRole.put(g.role(), g));
        this.guides = Collections.unmodifiableMap(byRole);
    }

    /** The duty guide of a role code, when one is shipped. */
    public Optional<RoleGuide> guide(String role) {
        return role == null ? Optional.empty() : Optional.ofNullable(guides.get(role));
    }

    /** Every shipped guide, by role code. */
    public Map<String, RoleGuide> guides() {
        return guides;
    }

    /** Menu key to plain name, from {@code help/screens.json}. */
    public Map<String, String> screenNames() {
        return screenNames;
    }

    /** Every article, sorted by id. */
    public List<HelpArticle> all() {
        return List.copyOf(articles.values());
    }

    /** The fixed list of deeper pages: path to parent screen. */
    public Map<String, String> subRoutes() {
        return subRoutes;
    }

    /** The articles this caller may see, sorted by id. */
    public List<HelpArticle> visibleTo(HelpViewer viewer) {
        return articles.values().stream().filter(viewer::maySee).toList();
    }

    /** The article, only when the caller may see it: a hidden id answers the same as an unknown one. */
    public Optional<HelpArticle> find(String id, HelpViewer viewer) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(articles.get(id.strip().toLowerCase(Locale.ROOT)))
                .filter(viewer::maySee);
    }

    /**
     * The catalogue of the caller's visible articles, sorted by id. Over {@link #CATALOGUE_CAP}, the articles written
     * for one of the caller's roles are kept first, then by id, and {@link HelpCatalogue#more()} is set.
     */
    public HelpCatalogue catalogueFor(HelpViewer viewer) {
        List<HelpArticle> visible = visibleTo(viewer);
        boolean more = visible.size() > CATALOGUE_CAP;
        if (more) {
            visible = visible.stream()
                    .sorted(Comparator.comparing((HelpArticle a) -> !viewer.writtenFor(a))
                            .thenComparing(HelpArticle::id))
                    .limit(CATALOGUE_CAP)
                    .sorted(Comparator.comparing(HelpArticle::id))
                    .toList();
        }
        return new HelpCatalogue(
                visible.stream()
                        .map(a -> new HelpCatalogue.Line(a.id(), a.title(), a.summary()))
                        .toList(),
                more);
    }

    /**
     * The article for a navigation item's path that the caller may see (for W-114.13): one with no {@code route} and
     * no {@code action} first, then the lowest id; empty when none.
     */
    public Optional<String> articleIdForScreen(String path, HelpViewer viewer) {
        if (path == null) {
            return Optional.empty();
        }
        return articles.values().stream()
                .filter(a -> a.screen().equals(path))
                .filter(viewer::maySee)
                .min(Comparator.comparing((HelpArticle a) -> a.route() != null || a.action() != null)
                        .thenComparing(HelpArticle::id))
                .map(HelpArticle::id);
    }

    /** The fallback search over the caller's visible articles (spec "The search rule"). */
    public List<HelpArticle> search(String query, HelpViewer viewer) {
        return HelpSearch.search(query, visibleTo(viewer), viewer.roles());
    }

    // ---- loading ----

    private static Map<String, String> loadSubRoutes(ResourcePatternResolver resolver, String pattern) {
        Map<String, String> routes = new LinkedHashMap<>();
        ObjectMapper json = new ObjectMapper();
        for (Resource resource : resources(resolver, pattern)) {
            try (InputStream in = resource.getInputStream()) {
                JsonNode list = json.readTree(in);
                if (list == null || !list.isArray()) {
                    throw new IllegalStateException("Help sub-routes " + resource.getFilename() + " must be a list");
                }
                for (JsonNode entry : list) {
                    String path = entry.path("path").asText("");
                    String parent = entry.path("parent").asText("");
                    if (path.isBlank() || parent.isBlank()) {
                        throw new IllegalStateException(
                                "Help sub-routes " + resource.getFilename() + ": every entry needs path and parent");
                    }
                    if (routes.putIfAbsent(path, parent) != null) {
                        throw new IllegalStateException("Help sub-routes: path " + path + " is listed twice");
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Help sub-routes " + resource.getFilename() + " cannot be read", e);
            }
        }
        return Map.copyOf(routes);
    }

    private static Map<String, HelpArticle> loadArticles(
            ResourcePatternResolver resolver, String pattern, Map<String, String> subRoutes) {
        Map<String, HelpArticle> loaded = new TreeMap<>();
        for (Resource resource : resources(resolver, pattern)) {
            String file = resource.getFilename();
            String text;
            try (InputStream in = resource.getInputStream()) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Help article " + file + " cannot be read", e);
            }
            HelpArticle article = parse(file, text, subRoutes);
            if (loaded.putIfAbsent(article.id(), article) != null) {
                throw new IllegalStateException("Help article " + file + ": id " + article.id() + " is used twice");
            }
        }
        return Collections.unmodifiableMap(loaded);
    }

    private static Map<String, String> loadScreenNames(ResourcePatternResolver resolver, String pattern) {
        Map<String, String> names = new TreeMap<>();
        ObjectMapper json = new ObjectMapper();
        for (Resource resource : resources(resolver, pattern)) {
            try (InputStream in = resource.getInputStream()) {
                JsonNode map = json.readTree(in);
                if (map == null || !map.isObject()) {
                    throw new IllegalStateException("Help screens " + resource.getFilename() + " must be an object");
                }
                Iterator<Map.Entry<String, JsonNode>> fields = map.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String name = entry.getValue().asText("").strip();
                    if (name.isEmpty()) {
                        throw new IllegalStateException("Help screens: " + entry.getKey() + " has no name");
                    }
                    if (names.putIfAbsent(entry.getKey(), name) != null) {
                        throw new IllegalStateException("Help screens: " + entry.getKey() + " is named twice");
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Help screens " + resource.getFilename() + " cannot be read", e);
            }
        }
        return Collections.unmodifiableMap(names);
    }

    private static Map<String, RoleGuide> loadGuides(
            ResourcePatternResolver resolver,
            String pattern,
            Map<String, HelpArticle> articles,
            Map<String, String> screenNames) {
        Map<String, RoleGuide> loaded = new TreeMap<>();
        for (Resource resource : resources(resolver, pattern)) {
            String file = resource.getFilename();
            String text;
            try (InputStream in = resource.getInputStream()) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Role guide " + file + " cannot be read", e);
            }
            RoleGuide guide = parseGuide(file, text, articles.keySet(), screenNames.keySet());
            if (loaded.putIfAbsent(guide.role(), guide) != null) {
                throw new IllegalStateException("Role guide " + file + ": role " + guide.role() + " is used twice");
            }
        }
        return Collections.unmodifiableMap(loaded);
    }

    /** One guide file to a guide, or an exception naming the file and the problem. */
    static RoleGuide parseGuide(String file, String raw, Set<String> articleIds, Set<String> screenKeys) {
        String text = raw.replace("\r\n", "\n");
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        if (!text.startsWith("---\n")) {
            throw badGuide(file, "has no front matter (the file must start with a --- line)");
        }
        int end = text.indexOf("\n---\n", 3);
        if (end < 0) {
            throw badGuide(file, "front matter is not closed by a --- line");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : text.substring(4, end).split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            int colon = line.indexOf(':');
            String key = colon <= 0 ? "" : line.substring(0, colon).strip();
            if (!List.of("role", "title").contains(key)) {
                throw badGuide(file, "front matter line '" + line + "' is not role: or title:");
            }
            if (fields.putIfAbsent(key, line.substring(colon + 1).strip()) != null) {
                throw badGuide(file, "field '" + key + "' is given twice");
            }
        }
        String role = fields.getOrDefault("role", "");
        String title = fields.getOrDefault("title", "");
        if (role.isBlank() || title.isBlank()) {
            throw badGuide(file, "role and title are required");
        }
        if (!ROLE_CODES.contains(role)) {
            throw badGuide(file, "unknown role code '" + role + "'");
        }
        if (!(role + ".md").equals(file)) {
            throw badGuide(file, "role '" + role + "' must equal the file name");
        }
        String body = text.substring(end + 5).strip();
        if (body.length() > MAX_GUIDE) {
            throw badGuide(file, "body is " + body.length() + " characters; at most " + MAX_GUIDE);
        }
        List<RoleGuide.Section> sections = new ArrayList<>();
        String heading = null;
        List<RoleGuide.Line> lines = new ArrayList<>();
        for (String line : body.split("\n")) {
            String row = line.strip();
            if (row.isEmpty()) {
                continue;
            }
            if (row.startsWith("## ")) {
                if (heading != null) {
                    sections.add(section(file, heading, lines));
                }
                heading = row.substring(3).strip();
                lines = new ArrayList<>();
            } else if (row.startsWith("- ")) {
                if (heading == null) {
                    throw badGuide(file, "line '" + row + "' comes before the first ## heading");
                }
                lines.add(guideLine(file, row.substring(2), articleIds, screenKeys));
            } else {
                throw badGuide(file, "line '" + row + "' is neither a ## heading nor a - duty");
            }
        }
        if (heading == null) {
            throw badGuide(file, "has no ## section");
        }
        sections.add(section(file, heading, lines));
        return new RoleGuide(role, title, sections);
    }

    private static RoleGuide.Section section(String file, String heading, List<RoleGuide.Line> lines) {
        if (lines.isEmpty()) {
            throw badGuide(file, "section '" + heading + "' has no duty line");
        }
        return new RoleGuide.Section(heading, lines);
    }

    private static RoleGuide.Line guideLine(String file, String row, Set<String> articleIds, Set<String> screenKeys) {
        String screen = null;
        String article = null;
        Matcher m = MARKER.matcher(row);
        while (m.find()) {
            String kind = m.group(1);
            String value = m.group(2).strip();
            if ("screen".equals(kind)) {
                if (screen != null) {
                    throw badGuide(file, "line '" + row + "' names more than one {screen:}");
                }
                if (!screenKeys.contains(value)) {
                    throw badGuide(file, "screen '" + value + "' has no name in help/screens.json");
                }
                screen = value;
            } else if ("article".equals(kind)) {
                if (article != null) {
                    throw badGuide(file, "line '" + row + "' names more than one {article:}");
                }
                if (!articleIds.contains(value)) {
                    throw badGuide(file, "article '" + value + "' is not a help article");
                }
                article = value;
            } else {
                throw badGuide(file, "unknown marker {" + kind + ":} in line '" + row + "'");
            }
        }
        if (screen == null) {
            throw badGuide(file, "line '" + row + "' has no {screen:}");
        }
        String words =
                MARKER.matcher(row).replaceAll("").replaceAll("\\s+", " ").strip();
        if (words.isEmpty()) {
            throw badGuide(file, "a line has no words");
        }
        return new RoleGuide.Line(words, screen, article);
    }

    private static IllegalStateException badGuide(String file, String problem) {
        return new IllegalStateException("Role guide " + file + ": " + problem);
    }

    private static Resource[] resources(ResourcePatternResolver resolver, String pattern) {
        try {
            Resource[] found = resolver.getResources(pattern);
            Arrays.sort(found, Comparator.comparing(r -> String.valueOf(r.getFilename())));
            return found;
        } catch (IOException e) {
            throw new IllegalStateException("Help files cannot be listed at " + pattern, e);
        }
    }

    /** One file to an article, or an exception naming the file and the problem. */
    static HelpArticle parse(String file, String raw, Map<String, String> subRoutes) {
        String text = raw.replace("\r\n", "\n");
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        if (!text.startsWith("---\n")) {
            throw bad(file, "has no front matter (the file must start with a --- line)");
        }
        int end = text.indexOf("\n---\n", 3);
        if (end < 0) {
            throw bad(file, "front matter is not closed by a --- line");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : text.substring(4, end).split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw bad(file, "front matter line '" + line + "' is not key: value");
            }
            String key = line.substring(0, colon).strip();
            if (!FIELDS.contains(key)) {
                throw bad(file, "unknown front matter field '" + key + "'");
            }
            if (fields.putIfAbsent(key, line.substring(colon + 1).strip()) != null) {
                throw bad(file, "field '" + key + "' is given twice");
            }
        }
        for (String field : REQUIRED) {
            if (fields.getOrDefault(field, "").isBlank()) {
                throw bad(file, "required field '" + field + "' is missing");
            }
        }
        String body = text.substring(end + 5).strip();
        if (body.isEmpty()) {
            throw bad(file, "has no body");
        }
        if (body.length() > MAX_BODY) {
            throw bad(file, "body is " + body.length() + " characters; at most " + MAX_BODY);
        }
        String id = fields.get("id");
        if (!ID.matcher(id).matches()) {
            throw bad(file, "id '" + id + "' must be lower case letters, digits and -");
        }
        if (!(id + ".md").equals(file)) {
            throw bad(file, "id '" + id + "' must equal the file name");
        }
        String title = fields.get("title");
        if (title.length() > MAX_TITLE) {
            throw bad(file, "title is over " + MAX_TITLE + " characters");
        }
        String summary = fields.get("summary");
        if (summary.length() > MAX_SUMMARY) {
            throw bad(file, "summary is over " + MAX_SUMMARY + " characters");
        }
        List<String> roles = list(fields.get("roles"));
        for (String role : roles) {
            if (!ROLE_CODES.contains(role)) {
                throw bad(file, "unknown role code '" + role + "'");
            }
        }
        String screen = fields.get("screen");
        if (!screen.startsWith("/")) {
            throw bad(file, "screen '" + screen + "' must be a path starting with /");
        }
        String route = blankToNull(fields.get("route"));
        if (route != null && !screen.equals(subRoutes.get(route))) {
            throw bad(file, "route '" + route + "' is not in help/sub-routes.json with parent " + screen);
        }
        String action = blankToNull(fields.get("action"));
        List<String> keywords = list(fields.get("keywords"));
        String moduleName = blankToNull(fields.get("module"));
        PlatformModule module = null;
        if (moduleName != null) {
            try {
                module = PlatformModule.valueOf(moduleName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw bad(file, "module '" + moduleName + "' must be hrms or payroll");
            }
        }
        return new HelpArticle(id, title, summary, roles, screen, route, action, keywords, body, module);
    }

    private static List<String> list(String value) {
        List<String> out = new ArrayList<>();
        for (String part : value.split(",")) {
            String word = part.strip().toLowerCase(Locale.ROOT);
            if (!word.isEmpty()) {
                out.add(word);
            }
        }
        return out;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static IllegalStateException bad(String file, String problem) {
        return new IllegalStateException("Help article " + file + ": " + problem);
    }
}
