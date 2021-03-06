package org.hl7.fhir.igtools.publisher.utils;

/*-
 * #%L
 * org.hl7.fhir.publisher.core
 * %%
 * Copyright (C) 2014 - 2019 Health Level 7
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.JSONUtil;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

public class IGRegistryMaintainer {

  public class PublicationEntry {
    private String name;
    private String version;
    private String fhirVersion;
    private String path;
    
    public PublicationEntry(String name, String version, String fhirVersion, String path) {
      super();
      this.name = name;
      this.version = version;
      this.fhirVersion = fhirVersion;
      this.path = path;
    }
    
    public String getName() {
      return name;
    }
    
    public String getVersion() {
      return version;
    }
    
    public String getFhirVersion() {
      return fhirVersion;
    }
    
    public String getPath() {
      return path;
    }
  }
  
  public class ImplementationGuideEntry {
    private String packageId;
    private String canonical;
    private String title;
    private String cibuild;
    private List<PublicationEntry> releases = new ArrayList<>();
    private List<PublicationEntry> candidates = new ArrayList<>();
    
    public ImplementationGuideEntry(String packageId, String canonical, String title) {
      super();
      this.packageId = packageId;
      this.canonical = canonical;
      this.title = title;
    }

    public String getPackageId() {
      return packageId;
    }

    public String getCanonical() {
      return canonical;
    }

    public String getTitle() {
      return title;
    }

    public List<PublicationEntry> getReleases() {
      return releases;
    }

    public List<PublicationEntry> getCandidates() {
      return candidates;
    }

  }

  private String path;
  private List<ImplementationGuideEntry> igs = new ArrayList<>();

  public IGRegistryMaintainer(String path) {
    this.path = path;
  }

  public ImplementationGuideEntry seeIg(String packageId, String canonical, String title) {
    ImplementationGuideEntry ig = new ImplementationGuideEntry(packageId, canonical, title);
    igs.add(ig);
    return ig;
  }

  public void seeCiBuild(ImplementationGuideEntry ig, String path) {
    ig.cibuild = path;
  }

  public void seeRelease(ImplementationGuideEntry ig, String name, String version, String fhirVersion, String path) {
    PublicationEntry p = new PublicationEntry(name, version, fhirVersion, path);
    ig.releases.add(p);
  }

  public void seeCandidate(ImplementationGuideEntry ig, String name, String version, String fhirVersion, String path) {
    PublicationEntry p = new PublicationEntry(name, version, fhirVersion, path);
    ig.candidates.add(p);
  }

  public void finish() throws JsonSyntaxException, FileNotFoundException, IOException {
    if (path != null) {
      // load the file
      JsonObject json = (JsonObject) new JsonParser().parse(TextFile.fileToString(path)); // use gson parser to preseve property order
      for (ImplementationGuideEntry ig : igs) {
        JsonObject e = JSONUtil.findByStringProp(json.getAsJsonArray("guides"), "npm-name", ig.packageId);
        if (e == null) {
          e = new JsonObject();
          json.getAsJsonArray("guides").add(e);
          e.addProperty("name", ig.title);
          e.addProperty("category", "??");
          e.addProperty("npm-name", ig.packageId);
          e.addProperty("description", "??");
          e.addProperty("authority", getAuthority(ig.canonical));
          e.addProperty("country", getCountry(ig.canonical));
          e.addProperty("history", getHistoryPage(ig.canonical));
          e.addProperty("canonical", ig.canonical);
          e.addProperty("ci-build", ig.cibuild);
          JsonArray a = new JsonArray();
          e.add("language", a);
          a.add(new JsonPrimitive("en"));
        } else {
          if (!e.has("canonical") || !e.get("canonical").getAsString().equals(ig.canonical)) {
            e.remove("canonical");
            e.addProperty("canonical", ig.canonical);
          }
          if (!e.has("ci-build") || !e.get("ci-build").getAsString().equals(ig.cibuild)) {
            e.remove("ci-build");
            e.addProperty("ci-build", ig.cibuild);
          }
        }
        if (e.has("editions")) {
          e.remove("editions");
        }
        JsonArray a = new JsonArray();
        e.add("editions", a);
        if (!ig.getCandidates().isEmpty()) {
          PublicationEntry p = ig.getCandidates().get(0);
          a.add(makeEdition(p, ig.packageId));
        }
        for (PublicationEntry p : ig.getReleases()) {
          a.add(makeEdition(p, ig.packageId));
        }
      }
      TextFile.stringToFile(new GsonBuilder().setPrettyPrinting().create().toJson(json), path, false);
    }
    for (ImplementationGuideEntry ig : igs) {
      System.out.println(ig.packageId+" ("+ig.canonical+"): "+ig.title+" @ "+ig.cibuild);
      for (PublicationEntry p : ig.getReleases()) {
        System.out.println("  release: "+p.name+" "+p.version+"/"+p.fhirVersion+" @ "+p.path);
      }
      if (!ig.getCandidates().isEmpty()) {
        PublicationEntry p = ig.getCandidates().get(0);
        System.out.println("  candidate: "+p.name+" "+p.version+"/"+p.fhirVersion+" @ "+p.path);
      }
    }    
  }
  
  private JsonObject makeEdition(PublicationEntry p, String packageId) {
    JsonObject e = new JsonObject();
    if (!e.has("name") || !e.get("name").getAsString().equals(p.getName())) {
      e.remove("name");
      e.addProperty("name", p.getName());
    }
    if (!e.has("ig-version") || !e.get("ig-version").getAsString().equals(p.getName())) {
      e.remove("ig-version");
      e.addProperty("ig-version", p.getVersion());
    }
    if (!e.has("package") || !e.get("package").getAsString().equals(packageId+"#"+p.getVersion())) {
      e.remove("package");
      e.addProperty("package", packageId+"#"+p.getVersion());
    }
    if (p.getFhirVersion() != null) {
      if (!e.has("fhir-version") || e.getAsJsonArray("fhir-version").size() != 1 || !e.getAsJsonArray("fhir-version").get(0).getAsString().equals(p.getName())) {
        e.remove("fhir-version");
        JsonArray a = new JsonArray();
        e.add("fhir-version", a);
        a.add(new JsonPrimitive(p.getFhirVersion()));
      } 
    } else if(e.has("fhir-version")) {
      e.remove("fhir-version");
    }
    if (!e.has("url") || !e.get("url").getAsString().equals(p.getPath())) {
      e.remove("url");
      e.addProperty("url", p.getPath());
    }
    return e;
  }

  private String getHistoryPage(String canonical) {
    return Utilities.pathURL(canonical, "history.html");
  }

  private String getCountry(String canonical) {
    if (canonical.contains("hl7.org")) {
      if (canonical.contains("/uv/"))
        return "uv";
      if (canonical.contains("/us/"))
        return "us";
    }
    return "??";
  }

  private String getAuthority(String canonical) {
    if (canonical.contains("hl7.org"))
      return "HL7";
    return "??";
  }

  public String getPath() {
    return path;
  }

}
