package org.example;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AkLegScraper {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Senator {
        public String name;
        public String title;
        public String party;
        public String profile;
        public String dob;
        public String type;
        public String country = "USA";
        public String url;
        public String otherinfo;
    }

    public static void main(String[] args) throws Exception {
        long t0 = System.currentTimeMillis();

        WebDriverManager.chromedriver().setup();
        ChromeOptions opts = new ChromeOptions();
        opts.addArguments("--headless=new", "--no-sandbox", "--disable-gpu");
        WebDriver driver = new ChromeDriver(opts);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        List<Senator> out = new ArrayList<>();
        try {
            driver.get("https://akleg.gov/senate.php");
            waitForReady(wait);

            List<WebElement> links = driver.findElements(By.xpath("//a[contains(@href,'legislator.php?id=')]"));
            LinkedHashMap<String, WebElement> unique = new LinkedHashMap<>();
            for (WebElement a : links) {
                String href = safeAttr(a, "href");
                if (href != null && href.contains("legislator.php?id=")) {
                    unique.putIfAbsent(href, a);
                }
            }

            for (Map.Entry<String, WebElement> entry : unique.entrySet()) {
                String profileUrl = entry.getKey();
                Senator s = new Senator();
                s.title = "Senator";
                s.url = profileUrl;

                String linkText = entry.getValue().getText().trim();
                if (!linkText.isBlank()) {
                    s.name = cleanName(linkText);
                }

                driver.navigate().to(profileUrl);
                waitForReady(wait);

                s.name = coalesce(s.name, textOrNull(driver,
                        By.xpath("//h1|//header//h1|//h2[contains(.,'Senator')]/preceding::*[self::h1 or self::h2][1]")));
                if (s.name != null) s.name = cleanName(s.name);

                String party = firstNonNull(
                        regexFind(driver.getPageSource(), "(Republican|Democrat(ic)?|Independent)", Pattern.CASE_INSENSITIVE),
                        textOrNull(driver, By.xpath("//*[contains(translate(., 'PARTY', 'party'),'party')]"))
                );
                if (party != null) {
                    party = normalizeParty(party);
                    s.party = party;
                }

                String position = regexFind(driver.getPageSource(),
                        "(Majority|Minority)\\s+(Leader|Whip)|President Pro Tempore|President of the Senate",
                        Pattern.CASE_INSENSITIVE);
                s.profile = position != null ? titleCase(position) : null;

                String pageText = visibleText(driver);
                String address = extractAddress(pageText);
                String phone = extractPhone(pageText);
                String email = extractEmail(driver);
                if (email == null) email = regexFind(pageText, "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);

                String type = regexFind(pageText, "(Anchorage|Fairbanks|Juneau|Mat[- ]?Su|Kenai|Wasilla|Palmer|Ketchikan|Sitka|Eagle River)", Pattern.CASE_INSENSITIVE);
                s.type = type != null ? titleCase(type) : null;

                StringBuilder oi = new StringBuilder();
                if (address != null) oi.append("Address: ").append(address);
                if (phone != null) {
                    if (oi.length() > 0) oi.append(" | ");
                    oi.append("Phone: ").append(phone);
                }
                if (email != null) {
                    if (oi.length() > 0) oi.append(" | ");
                    oi.append("Email: ").append(email);
                }
                s.otherinfo = oi.length() == 0 ? null : oi.toString();

                out.add(s);

                driver.navigate().back();
                waitForReady(wait);
            }

        } finally {
            driver.quit();
        }

        ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        Path outFile = Path.of("senators.json");
        Files.createDirectories(outFile.toAbsolutePath().getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(outFile.toFile(), out);

        long ms = System.currentTimeMillis() - t0;
        System.out.println("Wrote " + out.size() + " records to " + outFile.toAbsolutePath());
        System.out.println("Elapsed: " + ms + " ms");
    }

    private static void waitForReady(WebDriverWait wait) {
        wait.until((ExpectedCondition<Boolean>) wd -> {
            try {
                return ((JavascriptExecutor) wd).executeScript("return document.readyState").equals("complete");
            } catch (Exception e) {
                return false;
            }
        });
    }

    private static String safeAttr(WebElement el, String name) {
        try { return el.getAttribute(name); } catch (Exception e) { return null; }
    }

    private static String textOrNull(WebDriver driver, By by) {
        try {
            WebElement el = driver.findElement(by);
            String t = el.getText();
            return t == null || t.isBlank() ? null : t.trim();
        } catch (org.openqa.selenium.NoSuchElementException e) {
            return null;
        }
    }

    private static String regexFind(String haystack, String regex, int flags) {
        if (haystack == null) return null;
        Matcher m = Pattern.compile(regex, flags).matcher(haystack);
        return m.find() ? m.group().trim() : null;
    }

    private static String extractEmail(WebDriver driver) {
        try {
            WebElement mailto = driver.findElement(By.xpath("//a[starts-with(translate(@href,'MAILTO','mailto'),'mailto:')]"));
            String href = mailto.getAttribute("href");
            if (href != null && href.toLowerCase(Locale.ROOT).startsWith("mailto:")) {
                return href.substring(7).trim();
            }
            return null;
        } catch (org.openqa.selenium.NoSuchElementException e) {
            return null;
        }
    }

    private static String extractPhone(String text) {
        String p = regexFind(text, "(\\+?1[ .-]?)?\\(?\\d{3}\\)?[ .-]?\\d{3}[ .-]?\\d{4}", Pattern.CASE_INSENSITIVE);
        return p != null ? p.replaceAll("\\s+", " ").trim() : null;
    }

    private static String extractAddress(String text) {
        String block = regexFind(text, "(?s)(Capitol.*?Room.*?\\d+|State Capitol.*?\\d{5}(-\\d{4})?)", Pattern.CASE_INSENSITIVE);
        if (block == null) {
            block = regexFind(text, "(?s)(Juneau,? AK .*?\\d{5}(-\\d{4})?)", Pattern.CASE_INSENSITIVE);
        }
        return block != null ? block.replaceAll("\\s{2,}", " ").replaceAll("\\s*\\n\\s*", ", ").trim() : null;
    }

    private static String visibleText(WebDriver driver) {
        try {
            return (String)((JavascriptExecutor)driver).executeScript(
                    "return document.body.innerText || document.body.textContent || '';");
        } catch (Exception e) {
            return driver.getPageSource();
        }
    }

    private static String normalizeParty(String s) {
        String t = s.toLowerCase(Locale.ROOT);
        if (t.startsWith("rep")) return "Republican";
        if (t.startsWith("dem")) return "Democrat";
        if (t.startsWith("ind")) return "Independent";
        return titleCase(s);
    }

    private static String titleCase(String s) {
        String[] parts = s.trim().toLowerCase(Locale.ROOT).split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            parts[i] = parts[i].substring(0,1).toUpperCase(Locale.ROOT) + parts[i].substring(1);
        }
        return String.join(" ", parts);
    }

    private static String cleanName(String s) {
        return s.replaceAll("^(?i)senator\\s+", "").trim();
    }

    private static String firstNonNull(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static String coalesce(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}
