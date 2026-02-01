import * as functions from "firebase-functions";
import fetch from "node-fetch";

// The API keys are stored as Firebase environment secrets
// Set them using: firebase functions:secrets:set ODDS_API_KEY
//                 firebase functions:secrets:set ANTHROPIC_API_KEY
const ODDS_API_BASE_URL = "https://api.the-odds-api.com/v4";
const ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";

/**
 * Cloud Function to proxy requests to The-Odds-API.
 * This keeps the API key secure on the server side.
 *
 * Usage: GET /getOdds?sport=soccer_epl&regions=us,uk,eu&markets=h2h&oddsFormat=decimal
 */
export const getOdds = functions
  .runWith({
    secrets: ["ODDS_API_KEY"],
    timeoutSeconds: 30,
    memory: "256MB",
  })
  .https.onRequest(async (req, res) => {
    // CORS headers for Android app
    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Methods", "GET, OPTIONS");
    res.set("Access-Control-Allow-Headers", "Content-Type");

    if (req.method === "OPTIONS") {
      res.status(204).send("");
      return;
    }

    if (req.method !== "GET") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      const apiKey = process.env.ODDS_API_KEY;

      if (!apiKey) {
        console.error("ODDS_API_KEY secret not configured");
        res.status(500).json({ error: "Server configuration error" });
        return;
      }

      // Extract query parameters
      const {
        sport = "soccer_epl",
        regions = "us,uk,eu",
        markets = "h2h",
        oddsFormat = "decimal",
      } = req.query;

      // Build the API URL
      const apiUrl = new URL(`${ODDS_API_BASE_URL}/sports/${sport}/odds`);
      apiUrl.searchParams.set("apiKey", apiKey);
      apiUrl.searchParams.set("regions", String(regions));
      apiUrl.searchParams.set("markets", String(markets));
      apiUrl.searchParams.set("oddsFormat", String(oddsFormat));

      console.log(`Fetching odds for sport: ${sport}`);

      // Make the request to The-Odds-API
      const response = await fetch(apiUrl.toString());

      if (!response.ok) {
        const errorText = await response.text();
        console.error(`API error: ${response.status} - ${errorText}`);
        res.status(response.status).json({
          error: "Failed to fetch odds",
          details: errorText,
        });
        return;
      }

      const data = await response.json();

      // Log remaining requests (from headers) for monitoring
      const remainingRequests = response.headers.get("x-requests-remaining");
      const usedRequests = response.headers.get("x-requests-used");
      console.log(`API quota - Used: ${usedRequests}, Remaining: ${remainingRequests}`);

      res.status(200).json(data);
    } catch (error) {
      console.error("Error fetching odds:", error);
      res.status(500).json({
        error: "Internal server error",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

/**
 * Cloud Function to get available sports from The-Odds-API.
 */
export const getSports = functions
  .runWith({
    secrets: ["ODDS_API_KEY"],
    timeoutSeconds: 30,
    memory: "256MB",
  })
  .https.onRequest(async (req, res) => {
    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Methods", "GET, OPTIONS");
    res.set("Access-Control-Allow-Headers", "Content-Type");

    if (req.method === "OPTIONS") {
      res.status(204).send("");
      return;
    }

    if (req.method !== "GET") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      const apiKey = process.env.ODDS_API_KEY;

      if (!apiKey) {
        res.status(500).json({ error: "Server configuration error" });
        return;
      }

      const apiUrl = `${ODDS_API_BASE_URL}/sports?apiKey=${apiKey}`;
      const response = await fetch(apiUrl);

      if (!response.ok) {
        const errorText = await response.text();
        res.status(response.status).json({ error: errorText });
        return;
      }

      const data = await response.json();
      res.status(200).json(data);
    } catch (error) {
      console.error("Error fetching sports:", error);
      res.status(500).json({ error: "Internal server error" });
    }
  });

/**
 * Cloud Function to get match scores/results from The-Odds-API.
 * Used by the settlement worker to determine bet outcomes.
 *
 * Usage: GET /getScores?sport=soccer_epl&eventIds=abc123,def456&daysFrom=3
 */
export const getScores = functions
  .runWith({
    secrets: ["ODDS_API_KEY"],
    timeoutSeconds: 30,
    memory: "256MB",
  })
  .https.onRequest(async (req, res) => {
    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Methods", "GET, OPTIONS");
    res.set("Access-Control-Allow-Headers", "Content-Type");

    if (req.method === "OPTIONS") {
      res.status(204).send("");
      return;
    }

    if (req.method !== "GET") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      const apiKey = process.env.ODDS_API_KEY;

      if (!apiKey) {
        console.error("ODDS_API_KEY secret not configured");
        res.status(500).json({ error: "Server configuration error" });
        return;
      }

      const {
        sport = "soccer_epl",
        eventIds = "",
        daysFrom = "3",
      } = req.query;

      // Build the API URL for scores endpoint
      const apiUrl = new URL(`${ODDS_API_BASE_URL}/sports/${sport}/scores`);
      apiUrl.searchParams.set("apiKey", apiKey);
      apiUrl.searchParams.set("daysFrom", String(daysFrom));

      // Filter by specific event IDs if provided
      if (eventIds) {
        apiUrl.searchParams.set("eventIds", String(eventIds));
      }

      console.log(`Fetching scores for sport: ${sport}, eventIds: ${eventIds}`);

      const response = await fetch(apiUrl.toString());

      if (!response.ok) {
        const errorText = await response.text();
        console.error(`API error: ${response.status} - ${errorText}`);
        res.status(response.status).json({
          error: "Failed to fetch scores",
          details: errorText,
        });
        return;
      }

      const data = await response.json();

      // Log remaining requests for monitoring
      const remainingRequests = response.headers.get("x-requests-remaining");
      const usedRequests = response.headers.get("x-requests-used");
      console.log(`API quota - Used: ${usedRequests}, Remaining: ${remainingRequests}`);

      res.status(200).json(data);
    } catch (error) {
      console.error("Error fetching scores:", error);
      res.status(500).json({
        error: "Internal server error",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });

/**
 * Cloud Function to proxy AI analysis requests to Anthropic Claude API.
 * This keeps the API key secure on the server side.
 */
export const analyzeMatch = functions
  .runWith({
    secrets: ["ANTHROPIC_API_KEY"],
    timeoutSeconds: 60,
    memory: "512MB",
  })
  .https.onRequest(async (req, res) => {
    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
    res.set("Access-Control-Allow-Headers", "Content-Type");

    if (req.method === "OPTIONS") {
      res.status(204).send("");
      return;
    }

    if (req.method !== "POST") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      const apiKey = process.env.ANTHROPIC_API_KEY;

      if (!apiKey) {
        console.error("ANTHROPIC_API_KEY secret not configured");
        res.status(500).json({ error: "Server configuration error" });
        return;
      }

      const { system, messages, max_tokens = 512 } = req.body;

      if (!messages || !Array.isArray(messages)) {
        res.status(400).json({ error: "Missing or invalid messages" });
        return;
      }

      console.log("Calling Anthropic API for match analysis");

      const response = await fetch(ANTHROPIC_API_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-api-key": apiKey,
          "anthropic-version": "2023-06-01",
        },
        body: JSON.stringify({
          model: "claude-sonnet-4-20250514",
          max_tokens: max_tokens,
          system: system,
          messages: messages,
        }),
      });

      if (!response.ok) {
        const errorText = await response.text();
        console.error(`Anthropic API error: ${response.status} - ${errorText}`);
        res.status(response.status).json({
          error: "AI analysis failed",
          details: errorText,
        });
        return;
      }

      const data = await response.json();
      res.status(200).json(data);
    } catch (error) {
      console.error("Error in AI analysis:", error);
      res.status(500).json({
        error: "Internal server error",
        message: error instanceof Error ? error.message : "Unknown error",
      });
    }
  });
