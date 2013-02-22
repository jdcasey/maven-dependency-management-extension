package org.jboss.maven.extension.dependency.modelbuildingmodifier;

import java.util.HashMap;
import java.util.Map;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.codehaus.plexus.logging.Logger;
import org.jboss.maven.extension.dependency.util.Logging;
import org.jboss.maven.extension.dependency.util.SystemProperties;
import org.jboss.maven.extension.dependency.util.VersionOverride;

/**
 * Overrides dependency versions in a model
 */
public class DepVersionOverride
    extends VersionOverridePropertyUser
    implements ModelBuildingModifier
{
    private static final Logger logger = Logging.getLogger();

    /**
     * The String that needs to be prepended a system property to make it a version override. <br />
     * ex: -Dversion:junit:junit=4.10
     */
    private static final String VERSION_PROPERTY_NAME = "version" + PROPERTY_NAME_SEPERATOR;

    /**
     * Key: String of artifactID <br />
     * Value: Map of overrides for a groupID Inner Map Key: String of groupID Inner Map Value: String of desired
     * override version number
     */
    private final Map<String, Map<String, VersionOverride>> groupOverrideMap;

    /**
     * Load dependency overrides list when the object is instantiated
     */
    public DepVersionOverride()
    {
        Map<String, String> propertyMap = SystemProperties.getPropertiesByPrepend( VERSION_PROPERTY_NAME );

        HashMap<String, Map<String, VersionOverride>> groupOverrideMap =
            new HashMap<String, Map<String, VersionOverride>>();
        Map<String, VersionOverride> artifactOverrideMap;

        for ( String propertyName : propertyMap.keySet() )
        {
            // Split the name portion into parts (ex: junit:junit to {junit, junit})
            String[] propertyNameParts = propertyName.split( PROPERTY_NAME_SEPERATOR );

            if ( propertyNameParts.length == 2 )
            {
                // Part 1 is the group name. ex: org.apache.maven.plugins
                String groupID = propertyNameParts[0];
                // Part 2 is the artifact ID. ex: junit
                String artifactID = propertyNameParts[1];

                // The value of the property is the desired version. ex: 3.0
                String version = propertyMap.get( propertyName );

                logger.debug( "Detected version override property. Group: " + groupID + "  ArtifactID: " + artifactID
                    + "  Target Version: " + version );

                // Create VersionOverride object
                VersionOverride versionOverride = new VersionOverride( groupID, artifactID, version );

                // Insert the override into override map
                if ( groupOverrideMap.containsKey( groupID ) )
                {
                    artifactOverrideMap = groupOverrideMap.get( groupID );
                    artifactOverrideMap.put( artifactID, versionOverride );
                }
                else
                {
                    artifactOverrideMap = new HashMap<String, VersionOverride>();
                    artifactOverrideMap.put( artifactID, versionOverride );
                    groupOverrideMap.put( groupID, artifactOverrideMap );
                }
            }
            else
            {
                logger.error( "Detected bad version override property. Name: " + propertyName );
            }
        }

        if ( groupOverrideMap.size() == 0 )
        {
            logger.debug( "No version overrides." );
        }

        this.groupOverrideMap = groupOverrideMap;
    }

    @Override
    public ModelBuildingResult modifyBuild( ModelBuildingRequest request, ModelBuildingResult result )
    {
        for ( Dependency dependency : result.getEffectiveModel().getDependencies() )
        {
            String currGroupID = dependency.getGroupId();
            if ( groupOverrideMap.containsKey( currGroupID ) )
            {
                Map<String, VersionOverride> artifactOverrideMap = groupOverrideMap.get( currGroupID );
                String currArtifactID = dependency.getArtifactId();
                if ( artifactOverrideMap.containsKey( currArtifactID ) )
                {
                    String overrideVersion = artifactOverrideMap.get( currArtifactID ).getVersion();
                    String currVersion = dependency.getVersion();
                    if ( !currVersion.equals( overrideVersion ) )
                    {
                        dependency.setVersion( overrideVersion );
                        logger.debug( "Version of ArtifactID " + currArtifactID + " was overridden from " + currVersion
                            + " to " + dependency.getVersion() + " (" + overrideVersion + ")" );
                    }
                    else
                    {
                        logger.debug( "Version of ArtifactID " + currArtifactID
                            + " was the same as the override version (both are " + currVersion + ")" );
                    }
                    artifactOverrideMap.get( currArtifactID ).setOverriden( true );
                }
            }
        }

        // Add dependencies not already in model
        for ( String groupID : groupOverrideMap.keySet() )
        {
            for ( String artifactID : groupOverrideMap.get( groupID ).keySet() )
            {
                if ( !groupOverrideMap.get( groupID ).get( artifactID ).isOverriden() )
                {
                    String version = groupOverrideMap.get( groupID ).get( artifactID ).getVersion();
                    Dependency dependency = new Dependency();
                    dependency.setGroupId( groupID );
                    dependency.setArtifactId( artifactID );
                    dependency.setVersion( version );
                    result.getEffectiveModel().addDependency( dependency );
                    groupOverrideMap.get( groupID ).get( artifactID ).setOverriden( true );
                    logger.debug( "New dependency added: " + groupID + ":" + artifactID + "=" + version );
                }
            }
        }
        return result;
    }
}