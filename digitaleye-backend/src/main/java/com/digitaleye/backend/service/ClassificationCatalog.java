package com.digitaleye.backend.service;

import java.util.List;

import static com.digitaleye.backend.service.ActivityCategory.CREATIVE;
import static com.digitaleye.backend.service.ActivityCategory.NON_PRODUCTIVE;
import static com.digitaleye.backend.service.ActivityCategory.OTHER;
import static com.digitaleye.backend.service.ActivityCategory.PRODUCTIVE;
import static com.digitaleye.backend.service.ActivityCategory.TROUBLESHOOTING;

/**
 * The built-in knowledge of ActivityClassifier: which applications, websites and keywords belong
 * to which category. It is only data - the order of the lists IS the priority (first match wins),
 * so a more specific entry must be listed before a more general one.
 *
 * Matching is done on the lower-cased window title at word boundaries (see TextMatch). A phrase
 * ending in * is a prefix match. Process names (winword, taskmgr, msedge ...) are included next
 * to the window-title names so the catalog keeps working if the agent ever reports process names.
 */
final class ClassificationCatalog {

    private ClassificationCatalog() {
    }

    // ------------------------------------------------------------------ building blocks

    /** One catalog entry: a category, a short human label, and the phrases that select it. */
    static final class Rule {
        final ActivityCategory category;
        final String label;
        String[] phrases = new String[0];
        /** Must equal a whole title part (e.g. the application name after the last " - "). */
        String[] exact = new String[0];
        /** A soft rule is skipped when the title also carries a clear work signal. */
        boolean soft;

        Rule(ActivityCategory category, String label) {
            this.category = category;
            this.label = label;
        }

        Rule has(String... p) {
            this.phrases = p;
            return this;
        }

        Rule exact(String... e) {
            this.exact = e;
            return this;
        }

        Rule soft() {
            this.soft = true;
            return this;
        }
    }

    private static Rule rule(ActivityCategory category, String label) {
        return new Rule(category, label);
    }

    /** A platform that can be work or play depending on what is being watched/read there. */
    static final class Mixed {
        final String name;
        final String[] phrases;
        final ActivityCategory workCategory;
        final String workLabel;
        final ActivityCategory otherwiseCategory;
        final String otherwiseLabel;
        /** For search engines the "otherwise" can also be NON_PRODUCTIVE if the query itself is clearly personal. */
        final boolean search;

        Mixed(String name, String[] phrases, ActivityCategory workCategory, String workLabel,
              ActivityCategory otherwiseCategory, String otherwiseLabel, boolean search) {
            this.name = name;
            this.phrases = phrases;
            this.workCategory = workCategory;
            this.workLabel = workLabel;
            this.otherwiseCategory = otherwiseCategory;
            this.otherwiseLabel = otherwiseLabel;
            this.search = search;
        }
    }

    enum AiKind { ASSISTANT, CODING, CREATIVE, COMPANION }

    /** An AI tool. ASSISTANT tools are judged by what the title says the user is doing with them. */
    static final class Ai {
        final String name;
        final AiKind kind;
        final String[] phrases;
        /** For names that are ordinary words (Claude, Gemini): must be a whole title part. */
        final String[] exactPart;

        Ai(String name, AiKind kind, String[] phrases, String[] exactPart) {
            this.name = name;
            this.kind = kind;
            this.phrases = phrases;
            this.exactPart = exactPart;
        }
    }

    private static String[] p(String... values) {
        return values;
    }

    // ------------------------------------------------------------------ browsers

    /** Whole title part that names a browser (the part after the last " - "). */
    static final String[] BROWSER_NAMES = p(
            "google chrome", "chrome", "microsoft edge", "msedge", "edge", "mozilla firefox", "firefox",
            "brave", "brave browser", "opera", "opera gx", "opera browser", "vivaldi", "safari", "chromium",
            "tor browser", "duckduckgo", "duckduckgo browser", "arc", "yandex browser", "yandex", "iexplore",
            "internet explorer", "samsung internet", "uc browser", "waterfox", "librewolf", "floorp", "maxthon",
            "comet", "chatgpt atlas", "atlas", "zen browser", "zen", "min", "epic privacy browser", "avast secure browser",
            "microsoft edge dev", "microsoft edge beta", "microsoft edge canary", "google chrome beta",
            "google chrome canary", "firefox developer edition", "firefox nightly");

    /** Browser names that are safe to recognise even when a profile name follows them ("... - Google Chrome - Work"). */
    static final String[] BROWSER_NAMES_MULTIWORD = p(
            "google chrome", "microsoft edge", "mozilla firefox", "brave browser", "opera gx", "tor browser",
            "internet explorer", "yandex browser", "firefox developer edition");

    // ------------------------------------------------------------------ native applications

    /**
     * Desktop applications. Troubleshooting tools come first (their window titles are very short and
     * generic, e.g. "Settings"), then productive, creative, non-productive and finally neutral utilities.
     */
    static final List<Rule> NATIVE_RULES = List.of(

            // ---- Troubleshooting: Windows system tools -------------------------------------------
            rule(TROUBLESHOOTING, "System settings / admin tool")
                    .exact("settings", "windows settings", "task manager", "device manager", "control panel", "event viewer",
                            "registry editor", "services", "resource monitor", "performance monitor", "reliability monitor",
                            "disk management", "computer management", "system configuration", "system information",
                            "windows security", "task scheduler", "local group policy editor", "local users and groups",
                            "network connections", "network and sharing center", "windows update", "disk cleanup",
                            "windows memory diagnostic", "print management", "programs and features", "apps & features",
                            "installed apps", "system properties", "environment variables", "windows firewall",
                            "windows defender firewall", "component services", "odbc data source administrator",
                            "directx diagnostic tool", "get help", "feedback hub", "bluetooth & devices", "windows features",
                            "optional features", "power options", "startup apps", "default apps", "user account control",
                            "mmc", "regedit", "msconfig", "taskmgr", "devmgmt", "dxdiag", "perfmon",
                            "resmon", "eventvwr", "diskmgmt", "compmgmt", "services.msc", "systeminfo")
                    .has("task manager", "device manager", "control panel", "event viewer", "registry editor",
                            "resource monitor", "performance monitor", "reliability monitor", "disk management",
                            "computer management", "system configuration", "system information", "windows security",
                            "windows defender", "task scheduler", "group policy editor", "microsoft management console",
                            "windows troubleshooter", "troubleshoot*", "windows recovery", "recovery environment",
                            "bitlocker", "autoruns", "process explorer", "process monitor", "procmon", "sysinternals",
                            "hwinfo*", "cpu-z", "gpu-z", "crystaldiskinfo", "crystaldiskmark", "speccy", "ccleaner",
                            "malwarebytes", "revo uninstaller", "geek uninstaller", "driver booster", "snappy driver",
                            "intel driver & support assistant", "nvidia control panel", "windows memory diagnostic",
                            "disk cleanup", "system restore", "windows update", "network and sharing center"),
            rule(TROUBLESHOOTING, "Remote support tool")
                    .has("teamviewer", "anydesk", "rustdesk", "ultraviewer", "supremo remote", "remote desktop connection",
                            "quick assist", "chrome remote desktop", "realvnc", "vnc viewer", "tightvnc", "ultravnc",
                            "radmin", "mstsc", "remote desktop manager"),

            // ---- Productive: coding ---------------------------------------------------------------
            rule(PRODUCTIVE, "Code editor / IDE")
                    .exact("code", "idea", "idea64", "cursor", "notepad", "vim", "atom", "wordpad", "eclipse", "devenv",
                            "pycharm64", "studio64", "clion64", "webstorm64", "phpstorm64", "sublime_text", "spyder",
                            "rstudio", "matlab", "unity", "godot", "emacs", "nvim", "gvim", "brackets", "lazarus", "delphi")
                    .has("visual studio code", "vscode", "vs code", "vscodium", "visual studio", "intellij", "pycharm",
                            "webstorm", "phpstorm", "clion", "goland", "rubymine", "datagrip", "rustrover", "appcode",
                            "android studio", "eclipse ide", "eclipse", "netbeans", "jdeveloper", "code::blocks", "codeblocks",
                            "dev-c++", "dev c++", "devc++", "turbo c++", "borland c", "xcode", "sublime text", "notepad++",
                            "windsurf", "jupyter", "anaconda", "rstudio", "matlab", "arduino ide", "mplab", "keil",
                            "stm32cube", "thonny", "idle shell", "python shell", "unity editor", "unity hub", "unreal editor",
                            "unreal engine", "neovim", "qt creator", "code composer", "bluej", "greenfoot", "processing ide",
                            "mit app inventor", "scratch desktop", "visual basic", "vb.net"),
            rule(PRODUCTIVE, "Terminal / command line")
                    .exact("terminal", "cmd", "hyper", "ubuntu", "debian", "kali-linux", "wsl", "bash", "zsh", "pwsh",
                            "powershell")
                    .has("command prompt", "windows powershell", "powershell", "windows terminal", "git bash", "mingw64",
                            "mingw32", "cmd.exe", "system32\\cmd", "putty", "mobaxterm", "cmder", "conemu", "xterm", "konsole",
                            "gnome-terminal", "iterm", "alacritty", "wezterm"),
            rule(PRODUCTIVE, "Database / API / DevOps tool")
                    .exact("lens")
                    .has("mysql workbench", "dbeaver", "pgadmin", "sql server management studio", "ssms",
                            "oracle sql developer", "sql developer", "toad for", "mongodb compass", "robo 3t", "studio 3t",
                            "redis insight", "redisinsight", "heidisql", "navicat", "db browser for sqlite", "xampp", "wampserver",
                            "azure data studio", "postman", "insomnia", "swagger", "docker desktop", "github desktop",
                            "sourcetree", "gitkraken", "tortoisegit", "git gui", "filezilla", "winscp", "bitvise",
                            "minikube", "jenkins"),
            rule(PRODUCTIVE, "Engineering / security tool")
                    .has("autocad", "solidworks", "catia", "ansys", "fusion 360", "revit", "proteus", "multisim", "ltspice",
                            "simulink", "labview", "orcad", "kicad", "altium", "vivado", "quartus", "modelsim", "logisim",
                            "cadence", "autodesk inventor", "staad", "etabs", "primavera", "cisco packet tracer",
                            "packet tracer", "gns3", "eve-ng", "wireshark", "virtualbox", "vmware", "hyper-v manager",
                            "oracle vm", "burp suite", "zenmap", "nmap", "ghidra", "autopsy", "metasploit"),

            // ---- Productive: office and documents -------------------------------------------------
            rule(PRODUCTIVE, "Microsoft Office")
                    .exact("word", "excel", "powerpoint", "outlook", "onenote", "access", "publisher", "visio", "mail")
                    .has("microsoft word", "microsoft excel", "microsoft powerpoint", "microsoft outlook", "microsoft onenote",
                            "microsoft access", "microsoft publisher", "microsoft visio", "visio professional", "visio standard", "microsoft project", "ms project",
                            "winword", "powerpnt", "msaccess", "microsoft office", "microsoft 365", "office 365",
                            "power bi desktop", "power query editor", "power automate desktop", "power apps studio",
                            "sharepoint designer", "infopath", "skype for business", "microsoft planner", "microsoft to do"),
            rule(PRODUCTIVE, "Office suite / documents / PDF")
                    .exact("writer", "calc", "impress", "keynote", "pages")
                    .has("libreoffice", "libre office", "openoffice", "wps office", "wps writer", "wps spreadsheets",
                            "wps presentation", "wps pdf", "onlyoffice", "softmaker", "polaris office", "adobe acrobat",
                            "acrobat reader", "acrobat pro", "adobe reader", "foxit", "pdf-xchange", "sumatrapdf", "nitro pdf",
                            "pdfelement", "calibre", "okular", "evince", "zotero", "mendeley", "endnote", "texstudio",
                            "texmaker", "miktex", "overleaf", "lyx", "grammarly", "anki", "geogebra"),
            rule(PRODUCTIVE, "Communication / collaboration")
                    .exact("teams", "zoom", "slack", "skype", "notion", "loom")
                    .has("microsoft teams", "slack", "zoom workplace", "zoom meeting", "zoom.us", "webex", "cisco webex",
                            "ringcentral", "gotomeeting", "bluejeans", "mattermost", "rocket.chat", "zulip", "thunderbird",
                            "mailspring", "evernote", "obsidian", "joplin", "simplenote", "trello", "asana", "jira",
                            "confluence", "monday.com", "clickup", "basecamp", "airtable", "miro", "lucidchart", "draw.io",
                            "diagrams.net", "xmind", "mindmanager", "freemind", "smartdraw", "balsamiq", "astah", "staruml",
                            "enterprise architect", "visual paradigm"),
            rule(PRODUCTIVE, "Business / ERP / analytics app")
                    .exact("sas")
                    .has("sap gui", "sap logon", "sap business", "tallyprime", "tally erp", "quickbooks", "zoho",
                            "salesforce", "servicenow", "workday", "dynamics 365", "peoplesoft", "oracle", "tableau", "qlik",
                            "looker", "metabase", "knime", "rapidminer", "weka", "spss", "stata", "minitab"),

            // ---- Creative ---------------------------------------------------------------------------
            rule(CREATIVE, "Design / photo / video / audio editing")
                    .exact("paint", "sketch", "resolve", "blender", "maya", "krita", "gimp", "canva", "figma", "audacity", "reaper")
                    .has("photoshop", "illustrator", "lightroom", "indesign", "after effects", "premiere pro", "adobe premiere", "adobe xd",
                            "adobe animate", "adobe audition", "adobe media encoder", "adobe express", "adobe fresco",
                            "adobe dimension", "adobe character animator", "gimp", "inkscape", "krita", "paint.net",
                            "paint 3d", "microsoft paint", "mspaint", "sketchbook", "clip studio", "affinity photo",
                            "affinity designer", "affinity publisher", "coreldraw", "corel", "paintshop", "canva", "figma",
                            "blender", "autodesk maya", "3ds max", "cinema 4d", "zbrush", "sketchup", "houdini", "substance painter",
                            "substance designer", "daz studio", "davinci resolve", "final cut", "filmora", "wondershare",
                            "camtasia", "capcut", "vegas pro", "shotcut", "kdenlive", "openshot", "lightworks", "hitfilm",
                            "movavi", "clipchamp", "obs studio", "audacity", "fl studio", "ableton", "logic pro", "garageband",
                            "cubase", "pro tools", "studio one", "bandlab", "mixcraft", "lmms", "ardour", "wavepad",
                            "sound forge", "scribus", "lunacy", "penpot", "pencil2d", "synfig", "opentoonz", "toon boom",
                            "stop motion studio", "powtoon", "vyond", "animaker", "luminar", "capture one", "darktable",
                            "rawtherapee", "photoscape", "pixlr", "photopea", "fotor"),

            // ---- Non-productive: games, streaming, social, piracy ----------------------------------
            rule(NON_PRODUCTIVE, "Game / game launcher")
                    .exact("steam", "xbox", "chess", "solitaire", "origin", "celeste", "fallout", "doom", "halo", "blizzard",
                            "hearthstone", "battle.net")
                    .has("steam client", "steam big picture", "steamvr", "epic games launcher", "epic games", "ea app",
                            "ubisoft connect", "uplay", "gog galaxy", "xbox game bar", "riot client", "garena", "free fire",
                            "bgmi", "battlegrounds mobile", "pubg", "minecraft", "roblox", "fortnite", "counter-strike",
                            "counter strike", "cs2", "csgo", "cs:go", "dota 2", "valorant", "league of legends", "apex legends",
                            "call of duty", "warzone", "overwatch", "grand theft auto", "gta v", "gta san andreas",
                            "gta vice city", "fifa", "ea sports", "ea fc", "efootball", "pes 20", "nba 2k", "madden", "genshin",
                            "honkai", "zenless", "wuthering", "rocket league", "among us", "fall guys", "stumble guys",
                            "cyberpunk 2077", "the witcher", "elden ring", "red dead", "assassin's creed", "far cry",
                            "need for speed", "forza", "euro truck", "age of empires", "civilization vi", "stardew", "terraria",
                            "hollow knight", "the sims", "pokemon", "pok\u00e9mon", "diablo", "world of warcraft", "lost ark",
                            "path of exile", "destiny 2", "halo infinite", "doom eternal", "skyrim", "microsoft solitaire",
                            "solitaire collection", "minesweeper", "mahjong", "candy crush", "subway surfers", "temple run",
                            "clash of clans", "clash royale", "brawl stars", "8 ball pool", "ludo king", "bluestacks", "ldplayer",
                            "noxplayer", "nox player", "gameloop", "msi app player", "memu play", "ps remote play",
                            "geforce now", "playstation"),
            rule(NON_PRODUCTIVE, "Streaming / media player")
                    .exact("media player", "vlc", "spotify premium", "spotify free", "spotify")
                    .has("netflix", "prime video", "disney+", "hotstar", "jiocinema", "windows media player", "movies & tv",
                            "movies and tv", "groove music", "vlc media player", "kmplayer", "potplayer", "mpc-hc",
                            "media player classic", "gom player", "kodi", "plex", "emby", "jellyfin", "popcorn time", "stremio",
                            "mx player", "apple music", "itunes", "winamp", "foobar2000", "musicbee", "aimp", "tidal", "deezer",
                            "amazon music", "soundcloud", "twitch", "youtube music", "crunchyroll", "smplayer", "5kplayer",
                            "realplayer", "jriver"),
            rule(NON_PRODUCTIVE, "Social / dating app")
                    .has("instagram", "facebook", "snapchat", "tiktok", "twitter", "reddit", "pinterest", "tumblr", "tinder",
                            "bumble", "hinge", "likee", "bigo live", "omegle", "clubhouse", "sharechat"),
            rule(NON_PRODUCTIVE, "Torrent / consumer VPN / trading app")
                    .exact("deluge")
                    .has("utorrent", "\u00b5torrent", "bittorrent", "qbittorrent", "vuze", "limewire", "frostwire",
                            "nordvpn", "expressvpn", "protonvpn", "proton vpn", "surfshark", "cyberghost", "hotspot shield",
                            "windscribe", "tunnelbear", "psiphon", "ultrasurf", "hola vpn", "betternet", "turbo vpn",
                            "metatrader", "binance", "coinbase", "zerodha kite"),

            // ---- Neutral: messaging, VPN clients, utilities, file management ------------------------
            rule(OTHER, "Messaging")
                    .exact("signal")
                    .has("whatsapp", "telegram", "discord", "wechat", "viber"),
            rule(OTHER, "Corporate VPN client")
                    .has("cisco anyconnect", "anyconnect", "globalprotect", "forticlient", "pulse secure", "openvpn connect"),
            rule(OTHER, "Download manager")
                    .has("internet download manager", "free download manager"),
            rule(OTHER, "File management / utility")
                    .exact("file explorer", "this pc", "quick access", "downloads", "documents", "desktop", "pictures", "music",
                            "videos", "program manager", "calculator", "snipping tool", "snip & sketch", "sticky notes", "clock",
                            "alarms & clock", "weather", "maps", "photos", "microsoft store", "store", "calendar", "search",
                            "start", "cortana", "run", "open", "save as", "save", "print", "browse for folder", "select folder",
                            "choose file to upload", "file upload", "properties", "rename", "replace or skip files", "copying",
                            "moving", "extracting", "lock screen", "new notification", "notification center", "action center",
                            "task switching", "task view", "windows explorer", "windows default lock screen")
                    .has("winrar", "7-zip", "winzip", "adobe creative cloud", "windows default lock screen")
    );

    // ------------------------------------------------------------------ platforms that depend on what is being viewed

    static final List<Mixed> MIXED = List.of(
            new Mixed("YouTube", p("youtube", "youtu.be"),
                    PRODUCTIVE, "Educational / work video", NON_PRODUCTIVE, "Video streaming", false),
            new Mixed("Reddit", p("reddit"),
                    PRODUCTIVE, "Technical community", NON_PRODUCTIVE, "Social media", false),
            new Mixed("Quora", p("quora"),
                    PRODUCTIVE, "Technical Q&A", NON_PRODUCTIVE, "Social Q&A", false),
            new Mixed("Medium", p("medium", "substack", "blogspot"),
                    PRODUCTIVE, "Technical article", OTHER, "Blog / article", false),
            new Mixed("Vimeo", p("vimeo"),
                    PRODUCTIVE, "Educational / work video", OTHER, "Video", false),
            new Mixed("Web search", p("google search", "bing", "duckduckgo", "brave search", "yahoo search", "ecosia", "startpage"),
                    PRODUCTIVE, "Work-related search", OTHER, "Web search", true)
    );

    /** Handled before the mixed platforms so "YouTube Studio" is not mistaken for watching videos. */
    static final List<Rule> PRE_WEB_RULES = List.of(
            rule(CREATIVE, "Content creation").has("youtube studio")
    );

    // ------------------------------------------------------------------ AI tools

    static final List<Ai> AI_TOOLS = List.of(
            // coding assistants: always work
            new Ai("GitHub Copilot", AiKind.CODING, p("github copilot", "copilot chat"), p()),
            new Ai("Cursor / Windsurf / Codeium", AiKind.CODING,
                    p("cursor ai", "codeium", "tabnine", "windsurf", "claude code", "devin", "blackbox ai", "amazon q developer",
                            "gemini code assist", "jetbrains ai", "sourcegraph cody", "continue.dev", "aider", "replit agent"), p()),
            // creative generators
            new Ai("AI image / video / audio generator", AiKind.CREATIVE,
                    p("midjourney", "dall-e", "dalle", "leonardo.ai", "stable diffusion", "nightcafe", "dreamstudio",
                            "runway ml", "runwayml", "pika labs", "kling ai", "luma ai", "heygen", "synthesia", "d-id",
                            "elevenlabs", "suno", "udio", "lalal.ai", "murf", "speechify", "adobe firefly", "ideogram",
                            "playground ai", "krea.ai", "clipdrop", "magnific", "artbreeder"), p("sora", "runway", "descript")),
            // companion / entertainment chatbots
            new Ai("AI companion chatbot", AiKind.COMPANION,
                    p("character.ai", "character ai", "janitor ai", "janitorai", "replika", "chai ai", "talkie", "candy ai",
                            "crushon", "spicychat", "nomi.ai", "kindroid"), p()),
            // general assistants: judged by what the title says
            new Ai("ChatGPT", AiKind.ASSISTANT, p("chatgpt", "chat gpt", "openai", "gpt-4", "gpt-5", "chat.openai"), p()),
            new Ai("Claude", AiKind.ASSISTANT, p("claude.ai", "anthropic"), p("claude")),
            new Ai("Gemini", AiKind.ASSISTANT, p("google gemini", "gemini advanced", "gemini.google", "google ai studio", "notebooklm", "bard"),
                    p("gemini")),
            new Ai("Copilot", AiKind.ASSISTANT, p("microsoft copilot", "bing copilot", "copilot.microsoft"), p("copilot")),
            new Ai("Perplexity", AiKind.ASSISTANT, p("perplexity"), p()),
            new Ai("Lovable / app builder", AiKind.ASSISTANT, p("lovable", "bolt.new", "v0.dev", "v0 by vercel", "base44"), p()),
            new Ai("Other AI assistant", AiKind.ASSISTANT,
                    p("deepseek", "grok", "mistral ai", "le chat", "poe.com", "you.com", "phind", "meta ai", "qwen", "kimi",
                            "manus", "notion ai", "jasper", "copy.ai", "writesonic", "rytr", "wordtune", "gamma.app", "beautiful.ai",
                            "pi.ai", "huggingchat", "ai assistant", "ai chatbot", "ai chat", "chatbot", "otter.ai"), p())
    );

    /** Words in an AI chat title that suggest real work (added to the general productive keywords). */
    static final String[] AI_WORK_EXTRA = p(
            "code", "coding", "bug*", "error*", "exception*", "api", "query", "function*", "class", "script*", "regex",
            "deploy*", "test case*", "unit test*", "report", "email", "mail", "presentation", "slides", "ppt", "meeting*",
            "minutes", "proposal", "resume", "cover letter", "spec", "requirement*", "architecture", "design pattern*",
            "analysis", "analytics", "excel", "formula*", "spreadsheet", "dashboard", "synopsis", "abstract", "thesis", "research",
            "summar*", "assignment", "project*", "client", "customer", "invoice", "policy", "sop", "ticket", "incident", "sprint",
            "roadmap", "kpi", "okr", "budget", "forecast", "marketing", "seo", "business", "strategy", "interview question*",
            "job description", "performance review", "onboarding", "training", "workshop", "user story", "user stories",
            "uml", "wireframe", "prototype", "mvp", "startup", "pitch deck", "sales", "payroll", "game theory", "debug*",
            "refactor*", "optimi*", "migration", "schema", "backend", "frontend", "full stack", "web app", "mobile app",
            "pbl", "college project", "lab", "viva", "ppt");

    /** Words in an AI chat title that suggest personal / entertainment use. */
    static final String[] AI_PERSONAL = p(
            "movie*", "film*", "song*", "lyrics", "music", "playlist*", "joke*", "meme*", "story", "stories", "poem*", "poetry",
            "love", "crush", "girlfriend", "boyfriend", "dating", "relationship*", "horoscope*", "astrology", "zodiac", "recipe*",
            "cooking", "diet", "workout*", "gym", "fitness", "travel*", "vacation*", "trip", "holiday*", "itinerary", "hotel*",
            "flight*", "shopping", "gift*", "birthday*", "wedding", "caption*", "tweet*", "game*", "gaming", "cricket", "football",
            "ipl", "celebrity", "gossip", "netflix", "anime", "manga", "riddle*", "prank*", "roleplay", "role play", "role-play",
            "fanfic*", "fan fiction", "baby name*", "name ideas", "dream meaning", "lottery", "betting");

    // ------------------------------------------------------------------ web platforms and keywords

    static final List<Rule> WEB_PRODUCTIVE = List.of(
            rule(PRODUCTIVE, "Cloud / DevOps platform")
                    .has("aws", "amazon web services", "azure", "microsoft azure", "google cloud", "gcp", "cloud console", "management console",
                            "ibm cloud", "oracle cloud", "oci", "alibaba cloud", "digitalocean", "linode", "vultr", "heroku",
                            "vercel", "netlify", "cloudflare", "firebase", "supabase", "railway.app", "render.com", "openshift",
                            "red hat", "redhat", "rhel", "kubernetes", "k8s", "docker", "terraform", "hashicorp", "ansible",
                            "jenkins", "circleci", "travis ci", "argo cd", "argocd", "prometheus", "grafana", "datadog", "splunk",
                            "new relic", "dynatrace", "kibana", "elasticsearch", "elastic cloud", "logstash", "pagerduty",
                            "opsgenie", "sentry.io", "sonarqube", "sonarcloud", "snyk", "jfrog", "artifactory", "nexus repository",
                            "nginx", "apache tomcat", "apache kafka", "apache spark", "apache airflow", "apache hadoop",
                            "apache maven", "kafka", "rabbitmq", "databricks", "snowflake", "bigquery", "redshift"),
            rule(PRODUCTIVE, "Database platform")
                    .has("mongodb", "mongodb atlas", "redis", "mysql", "postgresql", "postgres", "mariadb", "sqlite",
                            "oracle database", "oracle", "sql server", "cassandra", "couchbase", "neo4j", "dynamodb", "cosmos db"),
            rule(PRODUCTIVE, "Developer platform / documentation")
                    .has("github", "gitlab", "bitbucket", "azure devops", "stack overflow", "stackoverflow", "stack exchange",
                            "npmjs", "npm", "pypi", "maven repository", "mvnrepository", "maven central", "nuget", "crates.io",
                            "packagist", "rubygems", "pkg.go.dev", "readthedocs", "gitbook", "dev.to", "hashnode", "codepen",
                            "jsfiddle", "codesandbox", "stackblitz", "replit", "glitch.com", "jsbin", "regex101", "jwt.io",
                            "postman", "swagger", "openapi", "developer.mozilla", "mdn web docs", "mozilla developer", "web.dev",
                            "devdocs", "javadoc", "spring.io", "spring boot", "spring framework", "hibernate", "baeldung",
                            "mkyong", "journaldev", "programiz", "tutorialspoint", "javatpoint", "w3schools", "geeksforgeeks",
                            "geeks for geeks", "gfg", "freecodecamp", "codecademy", "kaggle", "hugging face", "huggingface",
                            "tensorflow", "pytorch", "scikit-learn", "numpy", "pandas", "google colab", "colab", "wokwi",
                            "circuitverse", "wordpress"),
            rule(PRODUCTIVE, "Online compiler / coding practice")
                    .has("online compiler", "online ide", "online java", "online python", "online c++", "online c compiler",
                            "online gdb", "onlinegdb", "jdoodle", "ideone", "compiler explorer", "godbolt", "leetcode", "hackerrank",
                            "hackerearth", "codechef", "codeforces", "interviewbit", "atcoder", "topcoder", "coding ninjas",
                            "codewars", "exercism", "sololearn"),
            rule(PRODUCTIVE, "Learning platform")
                    .has("coursera", "udemy", "edx", "udacity", "nptel", "swayam", "khan academy", "pluralsight", "linkedin learning",
                            "simplilearn", "edureka", "great learning", "upgrad", "scaler", "skillshare", "datacamp", "brilliant.org",
                            "unacademy", "byju", "vedantu", "physics wallah", "pw skills", "infosys springboard", "tcs ion",
                            "ibm skillsbuild", "aws skill builder", "aws training", "microsoft learn", "cloud skills boost",
                            "oracle university", "red hat training", "cisco networking academy", "netacad", "comptia", "isc2",
                            "owasp", "hackthebox", "tryhackme", "portswigger", "cve.mitre", "nvd.nist", "exploit-db"),
            rule(PRODUCTIVE, "Office / collaboration web app")
                    .exact("meet")
                    .has("microsoft 365", "office.com", "office 365", "sharepoint", "onedrive", "microsoft teams", "teams.microsoft",
                            "outlook", "power bi", "power apps", "power automate", "microsoft forms", "microsoft planner",
                            "microsoft loop", "microsoft entra", "microsoft intune", "microsoft defender", "azure ad", "google docs",
                            "google sheets", "google slides", "google drive", "google forms", "google calendar", "google workspace",
                            "google classroom", "google keep", "google chat", "google sites", "google meet", "google scholar",
                            "google translate", "gmail", "mail.google", "zoho", "slack", "notion", "trello", "asana", "jira",
                            "confluence", "atlassian", "monday.com", "clickup", "basecamp", "airtable", "smartsheet", "wrike",
                            "linear.app", "miro", "mural.co", "lucidchart", "draw.io", "diagrams.net", "excalidraw", "zoom meeting",
                            "zoom workplace", "zoom.us", "webex", "loom.com", "fireflies", "tldv", "docusign", "adobe sign",
                            "calendly", "grammarly", "quillbot", "languagetool", "deepl", "wolfram alpha", "desmos", "symbolab",
                            "overleaf"),
            rule(PRODUCTIVE, "Business / enterprise platform")
                    .has("servicenow", "salesforce", "hubspot", "zendesk", "freshdesk", "freshworks", "freshservice", "intercom",
                            "workday", "sap", "successfactors", "netsuite", "oracle fusion", "peoplesoft", "dynamics 365", "tally",
                            "quickbooks", "bamboohr", "greythr", "keka", "darwinbox", "linkedin"),
            rule(PRODUCTIVE, "Research / reference")
                    .has("ieee", "acm digital library", "springer", "sciencedirect", "researchgate", "arxiv", "semantic scholar",
                            "jstor", "scopus", "web of science", "wikipedia", "britannica", "w3.org", "ietf", "rfc-editor")
    );

    static final List<Rule> WEB_CREATIVE = List.of(
            rule(CREATIVE, "Design / editing web tool")
                    .has("canva", "figma", "figjam", "adobe express", "lightroom", "adobe photoshop", "adobe illustrator", "adobe premiere", "photopea", "pixlr", "fotor", "befunky", "remove.bg", "removebg",
                            "picsart", "vectr", "boxy svg", "gravit designer", "capcut", "clipchamp", "kapwing", "veed", "invideo",
                            "animoto", "wevideo", "flexclip", "biteable", "powtoon", "vyond", "animaker", "renderforest", "soundtrap",
                            "bandlab", "soundation", "audiotool", "musescore", "flat.io", "noteflight", "splice", "framer", "webflow",
                            "wix", "squarespace", "tinkercad", "sketchfab", "thingiverse"),
            rule(CREATIVE, "Design inspiration / assets")
                    .has("behance", "dribbble", "awwwards", "unsplash", "pexels", "pixabay", "freepik", "shutterstock",
                            "getty images", "istock", "flaticon", "iconfinder", "google fonts", "dafont", "fontspace", "coolors",
                            "color hunt", "adobe color", "lottiefiles")
    );

    static final List<Rule> WEB_TROUBLESHOOTING = List.of(
            rule(TROUBLESHOOTING, "Support / diagnostics site")
                    .has("super user", "superuser", "server fault", "serverfault", "ask ubuntu", "askubuntu", "unix & linux",
                            "answers.microsoft", "microsoft community", "microsoft support", "support.microsoft", "windows help",
                            "apple support", "dell support", "hp support", "hp customer support", "lenovo support", "asus support",
                            "acer support", "intel support", "nvidia drivers", "driver download", "downdetector", "speedtest",
                            "fast.com", "whatismyip", "what is my ip", "dnschecker", "ping test")
    );

    static final List<Rule> WEB_NON_PRODUCTIVE = List.of(
            rule(NON_PRODUCTIVE, "Adult content")
                    .has("porn*", "xxx", "xvideos", "xnxx", "xhamster", "onlyfans", "chaturbate", "nsfw", "hentai"),
            rule(NON_PRODUCTIVE, "Piracy / torrent / cracked software")
                    .has("torrent*", "thepiratebay", "pirate bay", "1337x", "rarbg", "kickass", "limetorrents", "nyaa.si",
                            "crack download", "keygen", "free download full version", "cracked", "mod apk", "apkmod", "an1.com",
                            "happymod", "igetintopc", "getintopc", "filecr"),
            rule(NON_PRODUCTIVE, "Gambling / betting")
                    .has("casino", "lottery", "satta", "matka", "lotto", "poker", "rummy", "teen patti", "dream11", "mpl", "winzo",
                            "my11circle", "junglee", "pokerstars", "bet365", "betway", "1xbet", "stake.com", "draftkings", "fanduel",
                            "betting"),
            rule(NON_PRODUCTIVE, "Video / movie streaming")
                    .has("netflix", "prime video", "primevideo", "amazon prime", "disney+", "disneyplus", "hotstar", "jiohotstar",
                            "jiocinema", "hulu", "hbo max", "hbomax", "max.com", "peacock tv", "peacocktv", "paramount+",
                            "apple tv+", "tv.apple.com", "sonyliv", "sony liv", "zee5", "voot", "mx player", "altbalaji", "alt balaji",
                            "hoichoi", "sun nxt", "sunnxt", "eros now", "discovery+", "crunchyroll", "funimation", "hidive",
                            "gogoanime", "9anime", "aniwatch", "zoro.to", "animepahe", "kissanime", "fmovies", "123movies",
                            "putlocker", "solarmovie", "movierulz", "tamilrockers", "filmyzilla", "mp4moviez", "vegamovies",
                            "hdhub4u", "bolly4u", "tamilyogi", "isaimini", "9xmovies", "worldfree4u", "moviesflix", "yts.mx",
                            "twitch", "dailymotion", "bilibili", "tubi", "pluto tv", "imdb", "rotten tomatoes", "letterboxd",
                            "bookmyshow", "justwatch", "myanimelist", "anilist", "webtoon", "mangadex", "manganato", "mangakakalot",
                            "wattpad"),
            rule(NON_PRODUCTIVE, "Music streaming")
                    .has("spotify", "apple music", "youtube music", "music.youtube", "soundcloud", "deezer", "gaana", "jiosaavn",
                            "jio saavn", "wynk", "hungama", "bandcamp", "mixcloud", "audiomack", "last.fm", "pandora.com", "tidal.com"),
            rule(NON_PRODUCTIVE, "Social media")
                    .has("facebook", "instagram", "twitter", "x.com", "home / x", "notifications / x", "explore / x", "messages / x",
                            "bookmarks / x", "post / x", " on x:", "tiktok", "snapchat", "pinterest", "tumblr", "threads.net",
                            "bluesky", "mastodon", "9gag", "imgur", "buzzfeed", "boredpanda", "ifunny", "memedroid", "knowyourmeme",
                            "cheezburger", "giphy", "sharechat", "clubhouse", "likee", "bigo live", "omegle", "chatroulette",
                            "ome.tv", "vk.com", "weibo", "douyin"),
            rule(NON_PRODUCTIVE, "Dating / matrimony")
                    .has("tinder", "bumble", "hinge", "okcupid", "badoo", "happn", "shaadi", "jeevansathi", "bharatmatrimony",
                            "matrimony", "grindr", "meetme"),
            rule(NON_PRODUCTIVE, "Online games")
                    .has("miniclip", "poki", "crazygames", "y8.com", "y8 games", "friv", "kizi", "agame", "addictinggames",
                            "armor games", "kongregate", "newgrounds", "itch.io", "coolmathgames", "cool math games", "roblox",
                            "minecraft", "fortnite", "valorant", "league of legends", "op.gg", "u.gg", "dotabuff", "wordle",
                            "2048 game", "agar.io", "slither.io", "krunker", "shell shockers", "skribbl", "gartic", "tetris",
                            "chess.com", "lichess", "chess24", "online chess", "sudoku", "solitaire", "mahjong", "jigsaw", "ludo",
                            "free fire", "pubg", "bgmi", "garena", "genshin", "call of duty", "grand theft auto", "gta v",
                            "clash of clans", "candy crush", "among us", "steam sale", "steam deck", "steam store", "pokemon", "pok\u00e9mon",
                            "carrom", "steampowered", "steam community", "welcome to steam", "epic games", "gog.com", "gamespot",
                            "gamefaqs", "kotaku", "pcgamer", "eurogamer", "ign", "ea sports", "riot games"),
            rule(NON_PRODUCTIVE, "Online shopping")
                    .has("amazon", "flipkart", "myntra", "ajio", "meesho", "snapdeal", "nykaa", "tata cliq", "tatacliq",
                            "reliance digital", "croma", "vijay sales", "shopclues", "firstcry", "lenskart", "pepperfry",
                            "urban ladder", "ikea", "walmart", "best buy", "bestbuy", "costco", "ebay", "etsy", "aliexpress",
                            "alibaba", "shein", "temu", "zara", "h&m", "uniqlo", "nike", "adidas", "puma", "decathlon", "olx",
                            "quikr", "cars24", "cardekho", "carwale", "99acres", "magicbricks", "housing.com", "nobroker"),
            rule(NON_PRODUCTIVE, "Food delivery / travel booking")
                    .has("swiggy", "zomato", "blinkit", "zepto", "bigbasket", "instamart", "dunzo", "dominos", "domino's",
                            "pizza hut", "mcdonald", "kfc", "starbucks", "uber eats", "ubereats", "doordash", "grubhub",
                            "makemytrip", "goibibo", "yatra.com", "cleartrip", "ixigo", "easemytrip", "irctc", "redbus", "abhibus",
                            "booking.com", "agoda", "airbnb", "expedia", "tripadvisor", "skyscanner", "kayak", "oyo rooms"),
            rule(NON_PRODUCTIVE, "Sports / celebrity / gossip")
                    .has("espn", "cricbuzz", "cricinfo", "espncricinfo", "sportskeeda", "goal.com", "bleacher report", "livescore",
                            "sofascore", "flashscore", "bollywood hungama", "pinkvilla", "filmfare", "koimoi", "tmz", "horoscope*",
                            "astrosage", "tarot"),
            rule(NON_PRODUCTIVE, "Trading / crypto")
                    .has("zerodha", "groww", "upstox", "angel one", "5paisa", "icici direct", "moneycontrol", "tradingview",
                            "investing.com", "screener.in", "binance", "coinbase", "wazirx", "coindcx", "coinswitch", "kraken",
                            "bitcoin", "ethereum", "crypto*", "nft", "opensea", "metamask", "trust wallet", "forex", "metatrader")
                    .soft()
    );

    /** Pages that are neither clearly work nor play. Listed so they are labelled instead of reported as "unknown". */
    static final List<Rule> WEB_NEUTRAL = List.of(
            rule(OTHER, "Messaging")
                    .has("whatsapp", "telegram", "discord"),
            rule(OTHER, "Banking / payments")
                    .has("net banking", "netbanking", "internet banking", "online banking", "hdfc bank", "icici bank", "axis bank",
                            "kotak", "paytm", "phonepe", "google pay", "bhim", "paypal", "wise.com", "revolut"),
            rule(OTHER, "Job portal")
                    .has("naukri", "indeed.com", "glassdoor", "monster india", "foundit", "timesjobs", "shine.com", "apna.co",
                            "instahyre", "hirist", "wellfound", "angellist", "ziprecruiter", "job search", "jobs near"),
            rule(OTHER, "News")
                    .has("times of india", "hindustan times", "ndtv", "the hindu", "indian express", "news18", "aaj tak", "bbc",
                            "cnn", "reuters", "the guardian", "new york times", "washington post", "google news", "microsoft start",
                            "msn", "yahoo news", "inshorts", "dailyhunt", "flipboard"),
            rule(OTHER, "Maps / photos / utilities")
                    .has("google maps", "google photos", "google play", "weather", "calculator"),
            rule(OTHER, "Browser page")
                    .exact("new tab", "new tab page", "settings", "extensions", "history", "downloads", "bookmarks", "about:blank",
                            "untitled", "blank page", "loading...", "connecting...", "error", "page not found", "404",
                            "403 forbidden", "access denied", "privacy error", "your connection is not private", "sign in",
                            "log in", "login", "welcome", "home", "new window", "inprivate", "incognito")
                    .has("this site can't be reached", "err_internet_disconnected", "err_name_not_resolved", "attention required!")
    );

    // ------------------------------------------------------------------ generic keywords (checked after the named platforms)

    /** Strong signals that the content itself is technical, educational or business work. */
    static final String[] GENERIC_PRODUCTIVE = p(
            "tutorial*", "documentation", "docs", "api reference", "api documentation", "developer", "developers", "programming",
            "coding", "source code", "open source", "stack trace", "exception", "syntax", "algorithm*", "data structure*",
            "compiler", "debug*", "javascript", "typescript", "python", "java", "spring boot", "spring framework", "react",
            "angular", "vue", "node.js", "nodejs", "sql", "mysql", "postgresql", "mongodb", "kubernetes", "docker", "devops",
            "ci/cd", "cloud computing", "machine learning", "deep learning", "data science", "cybersecurity", "network security",
            "linux", "bash", "git", "github", "maven", "gradle", "jwt", "oauth", "rest api", "microservice*", "agile", "scrum",
            "jira", "confluence", "pull request*", "merge request*", "spring security", "spring data", "spring cloud",
            "spring mvc", "learnprogramming", "learnpython", "learnjava", "learnjavascript", "javahelp", "webdev", "sysadmin",
            "cscareerquestions", "datascience", "machinelearning", "compsci", "androiddev", "reactjs", "softwareengineering",
            "software", "web development", "app development", "android development", "game development",
            "game design", "game engine", "database", "lecture*", "online course", "courses", "full course", "crash course",
            "syllabus", "assignment", "e-learning", "certification", "interview questions", "operating system", "computer network",
            "dbms", "oops", "object oriented", "design pattern*", "uml", "flowchart", "software architecture", "quarterly report",
            "annual report", "project plan", "business plan", "proposal", "minutes of meeting", "agenda", "invoice",
            "purchase order", "spreadsheet", "presentation", "resume", "curriculum vitae", "job description", "research paper",
            "journal", "thesis", "dissertation");

    static final String[] GENERIC_TROUBLESHOOTING = p(
            "troubleshoot*", "not working", "won't turn on", "wont turn on", "blue screen", "bsod", "error code", "driver update",
            "drivers download", "wifi not", "no internet", "slow computer", "high cpu", "100% disk", "windows update error",
            "system restore", "safe mode", "reinstall windows", "factory reset", "bios", "ping test", "speed test", "dns",
            "ipconfig", "netstat", "fix error", "how to fix", "not responding", "keeps crashing", "won't boot", "black screen",
            "bluetooth not", "printer not", "usb not");

    static final String[] GENERIC_NON_PRODUCTIVE = p(
            "watch online", "watch free", "full movie", "web series", "movies online", "free movies", "movie", "movies", "film",
            "episode*", "trailer", "official music video", "music video", "lyrics", "karaoke", "online game*", "play game*",
            "free game*", "multiplayer", "gameplay", "walkthrough", "let's play", "game", "games", "gaming", "bollywood",
            "hollywood", "tollywood", "celebrity", "gossip", "meme*", "funny", "prank*", "viral", "horoscope*", "astrology",
            "zodiac", "dating", "matrimony", "fashion", "makeup", "shopping", "add to cart", "shopping cart", "buy online",
            "discount*", "coupon*", "deals", "wishlist", "live score", "ipl", "fifa", "premier league", "cricket", "football",
            "sports", "recipe*", "vacation*", "holiday packages", "tour packages", "porn*", "xxx", "nsfw", "download free",
            "free download", "cracked", "keygen", "cheats", "cheat codes", "mod apk", "play online", "games online", "songs",
            "song");

    // ------------------------------------------------------------------ file-type hints

    /** File extensions seen in a title when nothing else matched ("Main.java", "budget.xlsx", "logo.psd"). */
    static final List<Rule> EXTENSION_RULES = List.of(
            rule(PRODUCTIVE, "Source / config file")
                    .has("java", "kt", "kts", "py", "js", "ts", "jsx", "tsx", "c", "cpp", "cc", "h", "hpp", "cs", "go", "rs", "php",
                            "rb", "swift", "dart", "vue", "sql", "html", "css", "scss", "json", "xml", "yaml", "yml", "md", "sh",
                            "bat", "ps1", "gradle", "ipynb", "ino", "asm", "tex", "properties", "dockerfile", "toml", "dwg"),
            rule(PRODUCTIVE, "Office document")
                    .has("doc", "docx", "xls", "xlsx", "csv", "ppt", "pptx", "pdf", "odt", "ods", "odp", "rtf", "vsdx", "mpp"),
            rule(CREATIVE, "Design / media project file")
                    .has("psd", "psb", "xcf", "kra", "blend", "fig", "xd", "indd", "cdr", "afdesign", "afphoto", "aep", "prproj",
                            "drp", "kdenlive", "als", "flp", "aup3"),
            rule(NON_PRODUCTIVE, "Movie / torrent file")
                    .has("mkv", "avi", "torrent")
    );
}
