package cd.connect.prescan;

import com.bluetrainsoftware.classpathscanner.ClasspathScanner;
import com.bluetrainsoftware.classpathscanner.ResourceScanListener;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mojo(name = "prescan-config",
	defaultPhase = LifecyclePhase.PROCESS_CLASSES,
	configurator = "include-project-dependencies",
	requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class PrescanGeneratorMojo extends AbstractMojo {

	@Component
	MavenProjectHelper projectHelper;

	@Parameter(defaultValue = "${project}", readonly = true)
	MavenProject project;

	@Parameter(defaultValue = "${project.build.directory}")
	File projectBuildDir;

	@Parameter(defaultValue = "lib")
	String jarpath;


	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		List<String> interesting;

		try {
			if ( project != null ) {
				ClasspathScanner scanner = ClasspathScanner.getInstance();
				ClassLoader loader = getClass().getClassLoader();
				interesting = scan( scanner, loader );
				if( !interesting.isEmpty() ){
					// make sure the META-INF folder is there
					new File( projectBuildPath() + "/META-INF/" ).mkdirs();
					FileWriter writer = new FileWriter(projectBuildPath() + "/META-INF/prescan" );
					for( String item : interesting ) {
						writer.write(item + System.lineSeparator() );
					}
					writer.flush();
				} else {
					getLog().info( "No interesting web resources found?");
				}
			}
		} catch ( Exception ex ) {
			throw new MojoExecutionException( ex.getMessage(), ex );
		}
	}

	public List<String> scan( ClasspathScanner scanner, ClassLoader classLoader ) throws Exception {
		List<String> found = new ArrayList<>();

		ResourceScanListener listener = new ResourceScanListener() {
			@Override
			public List<ScanResource> resource( List<ScanResource> scanResources ) throws Exception {
				for ( ScanResource scanResource : scanResources ) {
					if ( scanResource.resourceName.endsWith( "WEB-INF/web.xml" ) ) {
						found.add( "webxml=" + mapScanResource( scanResource ) );
					} else if ( "META-INF/web-fragment.xml".equals( scanResource.resourceName ) ) {
						found.add( "fragment=" + mapScanResource( scanResource ) );
					} else if( scanResource.resourceName.startsWith( "META-INF/resources" ) ) {
						if ( isDirectory( scanResource ) ) {
							String path = mapScanResource( scanResource );
							if( !path.endsWith( "/" ) ) {
								path = path + "/";
							}
							found.add( "resource=" + path );
						}
					}
				}
				return null; // nothing was interesting :-)
			}

			@Override
			public void deliver( ScanResource scanResource, InputStream inputStream ) {
				// we don't care about the individual files or sub-folders so don't do anything
			}

			@Override
			public InterestAction isInteresting( InterestingResource interestingResource ) {
				String url = interestingResource.url.toString();
				if( url.contains( "jre" ) || url.contains( "jdk" ) ) {
					return InterestAction.NONE;
				} else {
					return InterestAction.ONCE;
				}
			}

			@Override
			public void scanAction( ScanAction action ) {
				// we don't care about the actions so don't do anything
			}
		};

		scanner.registerResourceScanner( listener );
		scanner.scan( classLoader );
		return found;
	}

	/**
	 * We need to dereference the path so we don't include the full path. When we are
	 * running inside mvn, our target path will end in /target so we adjust it so it
	 * points at /target/classes where all our stuff will be...
	 *
	 */
	private String mapScanResource( ResourceScanListener.ScanResource resource ){
		String url = resource.getResolvedUrl().toString();
		if( url.contains( "!" ) ){
			// process jar file.
			// The URL is going to be something like
			//    jar:file:/home/user/.m2/repository/org/bob/servlet/2.4.1.Final/servlet-2.4.1.Final.jar!/META-INF/web-fragment.xml
			// and it needs to look something like
			//   fragment=jar:file:/lib/servlet-2.4.1.Final.jar!/META-INF/web-fragment.xml

			String[] bits = url.split( "!" );
			String prefix = bits[ 0 ].substring( 0, bits[ 0 ].indexOf( "/" ) + 1 );
			String jarfile = bits[ 0 ].substring( bits[ 0 ].lastIndexOf( "/" ) );
			bits[ 0 ] = prefix + jarpath + jarfile;
			url = String.join( "!", bits );
		}
		return url.replace( projectBuildPath(), "" );

	}

	private String projectBuildPath() {
		String path = projectBuildDir.getPath();
		// are we in a test?
		if( !path.endsWith( "classes" ) ) {
			return path + "/classes";
		}
		return path;
	}

	private boolean isDirectory(ResourceScanListener.ScanResource scanResource ){
		return ( scanResource.file != null && scanResource.file.isDirectory() ) ||
						( scanResource.entry != null && scanResource.entry.isDirectory() );
	}
}
