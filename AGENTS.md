# Regeln für dieses Repo

> **Dieses Repo ist öffentlich.** Alles, was hier landet — Code, Texte, Commit-Nachrichten, Bilder —
> kann jeder lesen, auch nach dem Löschen (Verlauf). Im Zweifel: ins private Büro, nicht hierher.

Planung, Entscheidungen und Stand liegen im privaten Büro-Repo, nicht hier.

## Nie ins Repo (auch nicht in Commit-Texte)

- Keine **IP-Adressen**, Netzwerknamen, Seriennummern oder Gerätenamen aus dem echten Haushalt.
- Keine **Schlüssel**: kein ADB-Schlüssel (`adbkey`), kein Signier-Schlüssel, kein Token, keine Passwörter.
- Keine **privaten Infos**: keine Namen, keine Pfade vom eigenen Rechner, keine Screenshots vom eigenen TV-Inhalt.
- Keine **Markennamen im App-Namen oder Symbol** (Fire, Amazon, TCL …). In Texten nur beschreibend
  („funktioniert mit Fire TV“), nie so, dass es nach offizieller App aussieht.
- Kein Name einer Streaming-Seite oder eines Addons.

## Fremder Code

- Bibliotheken nur mit verträglicher Lizenz (Apache 2.0, MIT, BSD). Jede genutzte Bibliothek
  bekommt einen Eintrag im Lizenz-Bildschirm der App.
- Sonst kein fremder Code kopieren, nur Ideen übernehmen. Kein Trust-All-SSL.

## Code

- Alle Texte in `strings.xml`: Englisch in `values/`, Deutsch in `values-de/`. Kein Text fest im Code.
- Dateien unter 800 Zeilen, ein Thema pro Änderung.
- Vor jedem Hochladen selbst bauen und prüfen, Ergebnis im Thread zeigen.
