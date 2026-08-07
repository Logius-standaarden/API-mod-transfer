import { processRuleBlocks } from "https://logius-standaarden.github.io/publicatie/respec/plugins/adr.mjs";
import { loadRespecWithConfiguration } from "https://logius-standaarden.github.io/publicatie/respec/organisation-config.mjs";

loadRespecWithConfiguration({
  pubDomain: "api",
  shortName: "mod-transfer",
  specType: "HR",
  specStatus: "WV",
  publishDate: "2026-08-01",
  publishVersion: "0.0.0",
  previousPublishVersion: [],
  editors: [{
    name: "Logius Standaarden",
    company: "Logius",
    companyURL: "https://www.logius.nl",
  },],
  authors: [{
    name: "Logius Standaarden",
    company: "Logius",
    companyURL: "https://www.logius.nl",
  },],
  github: "https://github.com/Logius-standaarden/API-mod-transfer",
  localBiblio: {
    "httpbis-resumable-upload": {
      "authors": ["Marius Kleidl", "Guoye Zhang", "Lucas Pardue"],
      "href": "https://datatracker.ietf.org/doc/draft-ietf-httpbis-resumable-upload/",
      "publisher": "IETF",
      "title": "Resumable Uploads for HTTP",
      "status": "Active draft",
      "date": " 2026-07-06"
    },
  },
  postProcess: [processRuleBlocks],
});
