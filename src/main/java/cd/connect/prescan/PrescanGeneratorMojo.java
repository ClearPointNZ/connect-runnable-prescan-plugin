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
import java.io.InputStream;
import java.net.URL;
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

	@Parameter(defaultValue = "${project.build.directory}/generated-sources/dto/src/test/java")
	File javaOutFolder;

	@Parameter(defaultValue = "${project.directory}")
	File projectDir;


	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		try {
			if (project != null) {
				project.addCompileSourceRoot(javaOutFolder.getAbsolutePath());
			}
		} catch (Exception e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}
	}

	public void prescan( ClasspathScanner scanner, ClassLoader classLoader ) throws Exception {

		List<ResourceScanListener.ScanResource> resources = new ArrayList<>();




		ResourceScanListener listener = new ResourceScanListener() {
			@Override
			public List<ScanResource> resource( List<ScanResource> scanResources ) throws Exception {
				List<ResourceScanListener.ScanResource> interesting = new ArrayList<>();
				for ( ScanResource scanResource : scanResources ) {
					if( scanResource.resourceName.endsWith( "WEB-INF/web.xml" ) || "META-INF/web-fragment.xml".equals( scanResource.resourceName ) ) {
						resources.add( scanResource );
					} else if ( prefixWebResource( scanResource ) != null ) {
						interesting.add( scanResource );
					}
				}
				return interesting;
			}

			@Override
			public void deliver( ScanResource scanResource, InputStream inputStream ) {
				// this should only ever happen in production mode
//		String resourceName = scanResource.resourceName;
//		String stripPrefix = prefixWebResource(scanResource);

//
//		if (stripPrefix.length() > 0) {
//			resourceName = resourceName.substring(stripPrefix.length());
//		}
//
//		String[] paths = resourceName.split("/");
//		for (int count = 0; count < paths.length - 1; count++) {
//			InMemoryResource child = resource.findPath(paths[count]);
//
//			if (child == null) {
//				child = resource.addDirectory(paths[count]);
//			}
//
//			resource = child;
//		}
//
//		if (scanResource.entry.isDirectory()) {
//			resource.addDirectory(paths[paths.length - 1]);
//		} else {
//			resource.addFile(paths[paths.length - 1], stream);
//		}
				resources.add( scanResource );

			}

			@Override
			public InterestAction isInteresting( InterestingResource interestingResource ) {
				String url = interestingResource.url.toString();
				if ( url.contains( "jre" ) || url.contains( "jdk" ) ) {
					return InterestAction.NONE;
				} else {
					return InterestAction.ONCE;
				}
			}

			@Override
			public void scanAction( ScanAction action ) {
			}
		};

		scanner.registerResourceScanner( listener );
		scanner.scan( classLoader );
	}

	private String prefixWebResource( ResourceScanListener.ScanResource scanResource ) {
		return scanResource.resourceName.startsWith( "META-INF/resources/" ) ? "META-INF/resources/" : null;
	}

}
