# Ore ESP 1.3 – Minecraft Java 26.3 / Fabric

Quellprojekt. Die JAR wird mit **GitHub Actions** gebaut (Java 25 / Gradle 9.7.1). Es wurde hier **nicht kompiliert oder im Spiel getestet**.

- **X**: ESP ein-/ausschalten
- **G**: Einstellungen und **Diagnose im Chat anzeigen**
- **Reichweite**: 16 / 24 / 32 / 48 / 64 Blöcke
- **Seltene Erze** (Diamant, Smaragd, antiker Schrott) haben Vorrang gegenüber häufigen Erzen. Lava erhält maximal 80 Marker.
- **Bis zu 700 Markierungen**; Suche berücksichtigt räumliche Entfernung.
- **Flackerschutz**: zeigt vollständige Scanergebnisse statt halbfertige Durchläufe.
- **Diagnose**: Nach vollständig beendetem Scan zeigt der Button die Anzahl der dem Client bekannten, *aktivierten* Erzblöcke und die Anzahl markierter Positionen an. Die Zahlen sind keine echte Server-Erzstatistik.

WICHTIG: Kein Anti-X-Ray-Bypass. Werden Erzpositionen vom Server nicht an den Client gesendet, kann der Mod sie nicht anzeigen. Halte dich an die Regeln deiner Multiplayer-Server.

## GitHub-Upload
Repository anlegen, Projektinhalte hochladen. Wenn `.github` nicht hochgeladen wird, erstelle auf GitHub die Datei `.github/workflows/build.yml` und kopiere ihren Inhalt aus dem Projekt. Dann unter Actions bauen; nur die normale Mod-JAR (ohne `-sources`) verwenden.
