package io.lumify.it;

import io.lumify.web.clientapi.LumifyApi;
import io.lumify.web.clientapi.codegen.ApiException;
import io.lumify.web.clientapi.model.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static com.mongodb.util.MyAsserts.assertTrue;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class UploadRdfIntegrationTest extends TestBase {
    private static final String FILE_CONTENTS = getResourceString("sample.rdf");
    private String artifactVertexId;
    private String joeFernerVertexId;
    private String daveSingleyVertexId;
    private String altamiraCorporationVertexId;

    @Test
    public void testUploadRdf() throws IOException, ApiException {
        uploadAndProcessRdf();
        assertUser2CanSeeRdfVertices();
    }

    public void uploadAndProcessRdf() throws ApiException, IOException {
        LumifyApi lumifyApi = login(USERNAME_TEST_USER_1);
        addUserAuth(lumifyApi, USERNAME_TEST_USER_1, "auth1");

        ClientApiArtifactImportResponse artifact = lumifyApi.getVertexApi().importFile("auth1", "sample.rdf", new ByteArrayInputStream(FILE_CONTENTS.getBytes()));
        artifactVertexId = artifact.getVertexIds().get(0);

        lumifyTestCluster.processGraphPropertyQueue();

        assertPublishAll(lumifyApi, 25);

        ClientApiVertexSearchResponse searchResults = lumifyApi.getVertexApi().vertexSearch("*");
        LOGGER.info("searchResults (user1): %s", searchResults);
        assertEquals(4, searchResults.getVertices().size());
        for (ClientApiVertex v : searchResults.getVertices()) {
            assertEquals("auth1", v.getVisibilitySource());

            if (v.getId().equals("PERSON_Joe_Ferner")) {
                joeFernerVertexId = v.getId();
            }
            if (v.getId().equals("PERSON_Dave_Singley")) {
                daveSingleyVertexId = v.getId();
            }
            if (v.getId().equals("COMPANY_Altamira_Corporation")) {
                altamiraCorporationVertexId = v.getId();
            }
        }
        assertNotNull(joeFernerVertexId, "Could not find joe ferner");
        assertNotNull(daveSingleyVertexId, "Could not find dave singley");

        lumifyApi.logout();
    }

    private void assertUser2CanSeeRdfVertices() throws ApiException {
        LumifyApi lumifyApi = login(USERNAME_TEST_USER_2);
        addUserAuth(lumifyApi, USERNAME_TEST_USER_2, "auth1");

        assertSearch(lumifyApi);
        assertGetEdges(lumifyApi);
        assertFindPath(lumifyApi);
        assertFindRelated(lumifyApi);

        lumifyApi.logout();
    }

    private void assertFindRelated(LumifyApi lumifyApi) throws ApiException {
        ClientApiVertexFindRelatedResponse related = lumifyApi.getVertexApi().findRelated(joeFernerVertexId);
        assertEquals(2, related.getCount());
        assertEquals(2, related.getVertices().size());

        boolean foundAltamiraCorporation = false;
        boolean foundRdfDocument = false;
        for (ClientApiVertex v : related.getVertices()) {
            if (v.getId().equals(altamiraCorporationVertexId)) {
                foundAltamiraCorporation = true;
            }
            if (v.getId().equals(artifactVertexId)) {
                foundRdfDocument = true;
            }
        }
        assertTrue(foundAltamiraCorporation, "could not find AltamiraCorporation in related");
        assertTrue(foundRdfDocument, "could not find rdf in related");
    }

    private void assertSearch(LumifyApi lumifyApi) throws ApiException {
        ClientApiVertexSearchResponse searchResults = lumifyApi.getVertexApi().vertexSearch("*");
        LOGGER.info("searchResults (user2): %s", searchResults);
        assertEquals(4, searchResults.getVertices().size());
    }

    private void assertGetEdges(LumifyApi lumifyApi) throws ApiException {
        ClientApiVertexEdges artifactEdges = lumifyApi.getVertexApi().getEdges(artifactVertexId, null, null, null);
        assertEquals(3, artifactEdges.getTotalReferences());
        assertEquals(3, artifactEdges.getRelationships().size());
    }

    private void assertFindPath(LumifyApi lumifyApi) throws ApiException {
        ClientApiVertexFindPathResponse paths = lumifyApi.getVertexApi().findPath(joeFernerVertexId, daveSingleyVertexId, 2);
        LOGGER.info("paths: %s", paths.toString());
        assertEquals(2, paths.getPaths().size());
        boolean foundAltamiraCorporation = false;
        boolean foundRdfDocument = false;
        for (List<ClientApiVertex> path : paths.getPaths()) {
            assertEquals(3, path.size());
            if (path.get(1).getId().equals(altamiraCorporationVertexId)) {
                foundAltamiraCorporation = true;
            }
            if (path.get(1).getId().equals(artifactVertexId)) {
                foundRdfDocument = true;
            }
        }
        assertTrue(foundAltamiraCorporation, "could not find AltamiraCorporation in path");
        assertTrue(foundRdfDocument, "could not find rdf in path");
    }
}