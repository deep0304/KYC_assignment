# Alaska Senate Web Scraper (Java + Selenium)

## KYC2020 Assignment

## Overview
This project scrapes legislator information from the Alaska Senate directory and generates a structured JSON file containing contact and profile details.

**Source URL:**  
https://akleg.gov/senate.php

Each legislator profile is visited automatically, and relevant information is extracted into a single JSON array. This enables API-ready consumption, data analysis, or downstream integration.

---

## Extracted Fields
The scraper collects the following fields:

- **Name**
- **Title**
- **Party**
- **Address**
- **Phone**
- **Email**
- **URL (profile link)**
- **Type** (inferred region / city hint)
- **Country**
- **Other Info** (combined contact string)

Missing values gracefully map to `null`.

---

## Technologies Used
- Java 21
- Selenium WebDriver
- ChromeDriver (managed via WebDriverManager)
- Jackson Databind (JSON serialization)
- Maven (build automation)

---

## JSON Output

Example record:

```json
{
  "name": "FORREST DUNBAR",
  "title": "Senator",
  "party": "Democrat",
  "type": "Juneau",
  "country": "USA",
  "url": "https://akleg.gov/legislator.php?id=dnb",
  "otherinfo": "Address: State Capitol Room 125, Juneau AK, 99801 | Phone: 800-770-3822 | Email: Senator.Forrest.Dunbar@akleg.gov"
}
```

The file `senators.json` contains all records.

---

## Project Structure

```
akleg-scraper/
  ├── pom.xml
  ├── senators.json        (generated output)
  └── src/
      └── main/java/org/example/AkLegScraper.java
```

---

## Prerequisites
- Java 21 installed
- Maven installed
- Google Chrome installed (WSL requires manual install)
- Internet access

---

## Installation

Run:

```bash
mvn clean compile
```

This will download dependencies automatically.

---

## Running the Scraper

Execute:

```bash
mvn exec:java
```

If successful, output will show:

```
Wrote 20 records to /path/senators.json
Elapsed: 29515 ms
```

---

## Output Location

```bash
./senators.json
```

After running, open the JSON to verify.

---

## Approach Summary
1. Load senate listing page
2. Discover all legislator profile links
3. Navigate each profile
4. Extract structured text fields:
   - Generic DOM text scraping
   - Regex matching for contact info
5. Serialize objects into a single JSON array with Jackson

The scraper uses waits to ensure reliable page load execution.

---

## Error Handling
- Missing fields → `null`
- Invalid selectors safely caught
- Driver mismatch prevented using WebDriverManager

---

## Challenges & Mitigations
| Challenge | Resolution |
|----------|------------|
Chrome not found in WSL | Installed Chrome inside WSL / set binary path |
Inconsistent formatting | Used regex text normalization |
Missing DOB on site | Returned `null` by design |

---

## Time Taken

Total: `4 to 5 hours`


---

## Conclusion
This scraper:
- Collects all Alaska Senate legislator metadata
- Normalizes contact information
- Outputs a clean and consistent JSON dataset
- Uses industry-standard automation tooling



