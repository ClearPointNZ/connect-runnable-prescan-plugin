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
import java.util.List;

@Mojo(name = "generate",
	defaultPhase = LifecyclePhase.PROCESS_CLASSES,
	configurator = "include-project-dependencies",
	requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class PrescanGeneratorMojo extends AbstractMojo {

	@Component
	MavenProjectHelper projectHelper;

	@Parameter(defaultValue = "${project}", readonly = true)
	MavenProject project;

	@Parameter(defaultValue = "${project.targetDir}")
	File targetDir;




	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		List<String> interesting;

		try {
			if (project != null) {
				ClasspathScanner scanner = new ClasspathScanner();
				ClassLoader loader = getClass().getClassLoader();
				interesting = scan( scanner, loader );
				if( !interesting.isEmpty() ){
					FileWriter writer = new FileWriter("prescan" );
					for( String item : interesting ) {
						writer.write(item + System.lineSeparator() );
					}
					writer.flush();
				}
			}
		} catch (Exception ex) {
			throw new MojoExecutionException(ex.getMessage(), ex);
		}
	}

	public List<String> scan( ClasspathScanner scanner, ClassLoader classLoader ) throws Exception {

		List<String> found = new ArrayList<>();

		List<ResourceScanListener.ScanResource> webxml = new ArrayList<>();
		List<ResourceScanListener.ScanResource> fragments = new ArrayList<>();
		List<ResourceScanListener.ScanResource> resources = new ArrayList<>();

		ResourceScanListener listener = new ResourceScanListener() {
			@Override
			public List<ScanResource> resource(List<ScanResource> scanResources) throws Exception {
				for (ScanResource scanResource : scanResources) {
					if (scanResource.resourceName.endsWith("WEB-INF/web.xml")) {
						found.add( "webxml=" + mapScanResource( scanResource ) );
					} else if ("META-INF/web-fragment.xml".equals(scanResource.resourceName)) {
						found.add( "fragment=" + mapScanResource( scanResource ) );
					} else if ( scanResource.resourceName.startsWith( "META-INF/resources" ) ) {
						if( scanResource.file != null && scanResource.file.isDirectory() ) {
							found.add( "resource=" + mapScanResource( scanResource ) );
						}
					}
				}
				return null; // nothing was interesting :-)
			}

			@Override
			public void deliver(ScanResource scanResource, InputStream inputStream) {
				// we don't care about the individual files or sub-folders so don't do anything
			}

			@Override
			public InterestAction isInteresting(InterestingResource interestingResource) {
				String url = interestingResource.url.toString();
				if (url.contains("jre") || url.contains("jdk")) {
					return InterestAction.NONE;
				} else {
					return InterestAction.ONCE;
				}
			}

			@Override
			public void scanAction(ScanAction action) {
				// we don't care about the actions so don't do anything
			}
		};

		scanner.registerResourceScanner(listener);
		scanner.scan(classLoader);
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
		String path = targetDir.getPath();
		// are we in a test?
		if( !path.endsWith( "classes" ) ) {
			path = path + "/classes";
		}
		return url.replace( path, "" );

	}
}
