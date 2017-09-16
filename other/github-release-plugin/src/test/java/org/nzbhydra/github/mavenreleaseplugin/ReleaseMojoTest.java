package org.nzbhydra.github.mavenreleaseplugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class ReleaseMojoTest extends AbstractMojoTestCase {

    private ObjectMapper objectMapper = new ObjectMapper();

    public void testExecute() throws Exception {
        MockWebServer server = getMockWebServer();
        HttpUrl url = server.url("/repos/theotherp/nzbhydra2/releases");

        //Here the magic happens
        File pom = getTestFile("/src/test/resources/org/nzbhydra/github/mavenreleaseplugin/pomWithToken.xml");
        assertTrue(pom.exists());
        ReleaseMojo releaseMojo = new ReleaseMojo();
        releaseMojo = (ReleaseMojo) configureMojo(releaseMojo, extractPluginConfiguration("github-release-plugin", pom
        ));
        releaseMojo.githubReleasesUrl = url.toString();
        releaseMojo.windowsAsset = getTestFile("src/test/resources/org/nzbhydra/github/mavenreleaseplugin/windowsAsset.txt");
        releaseMojo.linuxAsset = getTestFile("src/test/resources/org/nzbhydra/github/mavenreleaseplugin/linuxAsset.txt");

        releaseMojo.execute();
        verifyExecution(server);
    }


    public void dontTestActual() throws Exception {
        //Here the magic happens
        File pom = getTestFile("/src/test/resources/org/nzbhydra/github/mavenreleaseplugin/pomWithToken.xml");
        assertTrue(pom.exists());
        ReleaseMojo releaseMojo = new ReleaseMojo();
        releaseMojo = (ReleaseMojo) configureMojo(releaseMojo, extractPluginConfiguration("github-release-plugin", pom
        ));
        releaseMojo.githubReleasesUrl = "https://api.github.com/repos/theotherp/nzbhydra2/releases";
        releaseMojo.githubTokenFile = new File("c:\\Users\\strat\\IdeaProjects\\NzbHydra2\\main\\token.txt");
        releaseMojo.windowsAsset = getTestFile("src/test/resources/org/nzbhydra/github/mavenreleaseplugin/windowsAsset.txt");
        releaseMojo.linuxAsset = getTestFile("src/test/resources/org/nzbhydra/github/mavenreleaseplugin/linuxAsset.txt");
        releaseMojo.changelogJsonFile = getTestFile("src/test/resources/org/nzbhydra/github/mavenreleaseplugin/changelog-001.json");
        releaseMojo.tagName = "v0.0.1";
        releaseMojo.commitish = "master";

        releaseMojo.execute();
    }

    public void testExecuteWithMissingChangelogEntry() throws Exception {
        File pom = getTestFile("/src/test/resources/org/nzbhydra/github/mavenreleaseplugin/pomWithChangelogWrongLatestEntry.xml");
        assertTrue(pom.exists());
        ReleaseMojo releaseMojo = new ReleaseMojo();
        releaseMojo = (ReleaseMojo) configureMojo(releaseMojo, extractPluginConfiguration("github-release-plugin", pom
        ));
        releaseMojo.windowsAsset = getTestFile("src/test/resources/org/nzbhydra/github/mavenreleaseplugin/windowsAsset.txt");
        releaseMojo.linuxAsset = getTestFile("src/test/resources/org/nzbhydra/github/mavenreleaseplugin/linuxAsset.txt");

        try {
            releaseMojo.execute();
            fail("Expected mojo exception");
        } catch (MojoExecutionException e) {
            assertTrue(e.getMessage().contains("Latest changelog entry version v0.0.1 does not match tag name v1.0.0"));
        }
    }

    public void testExecuteWithoutToken() throws Exception {
        File pom = getTestFile("/src/test/resources/org/nzbhydra/github/mavenreleaseplugin/pomWithoutToken.xml");
        assertTrue(pom.exists());
        ReleaseMojo releaseMojo = new ReleaseMojo();
        releaseMojo = (ReleaseMojo) configureMojo(releaseMojo, extractPluginConfiguration("github-release-plugin", pom
        ));
        releaseMojo.githubReleasesUrl = "http://127.0.0.1";
        releaseMojo.windowsAsset = getTestFile("src/test/resources/org/nzbhydra/github/mavenreleaseplugin/windowsAsset.txt");
        releaseMojo.linuxAsset = getTestFile("src/test/resources/org/nzbhydra/github/mavenreleaseplugin/linuxAsset.txt");

        try {
            releaseMojo.execute();
            fail("Expected mojo exception");
        } catch (MojoExecutionException e) {
            assertTrue(e.getMessage().contains("GitHub Token and GitHub token file not set"));
        }
    }

    public void testExecuteWithTokenFile() throws Exception {
        MockWebServer server = getMockWebServer();
        HttpUrl url = server.url("/repos/theotherp/nzbhydra2/releases");

        File pom = getTestFile("/src/test/resources/org/nzbhydra/github/mavenreleaseplugin/pomWithTokenFile.xml");
        assertTrue(pom.exists());
        ReleaseMojo releaseMojo = new ReleaseMojo();
        releaseMojo = (ReleaseMojo) configureMojo(releaseMojo, extractPluginConfiguration("github-release-plugin", pom
        ));
        releaseMojo.githubReleasesUrl = url.toString();
        releaseMojo.windowsAsset = getTestFile("src/test/resources/org/nzbhydra/github/mavenreleaseplugin/windowsAsset.txt");
        releaseMojo.linuxAsset = getTestFile("src/test/resources/org/nzbhydra/github/mavenreleaseplugin/linuxAsset.txt");

        releaseMojo.execute();

        verifyExecution(server);
    }

    protected void verifyExecution(MockWebServer server) throws InterruptedException, IOException {
        //Creating the release
        verifyDraftReleaseIsCreated(server);

        //Uploading the assets
        RecordedRequest windowsAssetUploadRequest = server.takeRequest(2, TimeUnit.SECONDS);
        assertTrue(windowsAssetUploadRequest.getPath(), windowsAssetUploadRequest.getPath().contains("releases/1/assets?name=windowsAsset.txt"));
        RecordedRequest linuxAssetUploadRequest = server.takeRequest(2, TimeUnit.SECONDS);
        assertTrue(linuxAssetUploadRequest.getPath(), linuxAssetUploadRequest.getPath().contains("releases/1/assets?name=linuxAsset.txt"));

        //Setting it effective
        RecordedRequest setEffectiveRequest = server.takeRequest(2, TimeUnit.SECONDS);
        assertTrue(setEffectiveRequest.getPath(), setEffectiveRequest.getPath().contains("releases/1"));
        String body = new String(setEffectiveRequest.getBody().readByteArray());
        Release bodyJson = objectMapper.readValue(body, Release.class);
        assertFalse(bodyJson.isDraft());
    }


    private MockWebServer getMockWebServer() throws JsonProcessingException {
        MockWebServer server = new MockWebServer();
        Release draftReleaseResponse = new Release();
        draftReleaseResponse.setUploadUrl(server.url("/repos/theotherp/nzbhydra2/releases/1/assets").toString());
        draftReleaseResponse.setUrl(server.url("/repos/theotherp/nzbhydra2/releases/1").toString());
        draftReleaseResponse.setDraft(true);

        ArrayList<Asset> assets = new ArrayList<>();
        assets.add(new Asset());
        assets.add(new Asset());
        draftReleaseResponse.setAssets(assets);
        Release effectiveReleaseResponse = new Release();

        effectiveReleaseResponse.setDraft(false);
        MockResponse releaseMockResponse = new MockResponse()
                .setResponseCode(200)
                .setBody(objectMapper.writeValueAsString(draftReleaseResponse));
        server.enqueue(releaseMockResponse);
        server.enqueue(new MockResponse().setResponseCode(200)); //Windows asset upload
        server.enqueue(new MockResponse().setResponseCode(200)); //Linux asset upload
        server.enqueue(new MockResponse().setResponseCode(200).setBody(objectMapper.writeValueAsString(effectiveReleaseResponse))); //Setting the release effective
        return server;
    }


    protected void verifyDraftReleaseIsCreated(MockWebServer server) throws InterruptedException, IOException {
        RecordedRequest releaseRequest = server.takeRequest(2, TimeUnit.SECONDS);
        assertTrue(releaseRequest.getRequestLine(), releaseRequest.getPath().endsWith("access_token=token"));

        String body = new String(releaseRequest.getBody().readByteArray());
        Release bodyJson = objectMapper.readValue(body, Release.class);
        assertEquals("v1.0.0", bodyJson.getTagName());
        assertFalse(bodyJson.isPrerelease());
        assertEquals("commitish", bodyJson.getTargetCommitish());
        assertTrue(bodyJson.isDraft());
        assertEquals("v1.0.0", bodyJson.getName());
        assertEquals("### v1.0.0\n" +
                "Note: First major release\n", bodyJson.getBody());
    }


}