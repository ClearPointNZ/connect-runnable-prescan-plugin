package cd.connect.prescan;

import com.bluetrainsoftware.classpathscanner.ClasspathScanner;
import com.bluetrainsoftware.classpathscanner.ResourceScanListener;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mojo(name = "prescan-config",
	defaultPhase = LifecyclePhase.PROCESS_CLASSES,
	requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class PrescanGeneratorMojo extends AbstractMojo {
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
			if (project != null) {
				URLClassLoader loader = makeClassLoaderFromProjectDependencies();

				ClasspathScanner scanner = ClasspathScanner.getInstance();
				interesting = scan( scanner, loader );
				if( !interesting.isEmpty() ){
					// make sure the META-INF folder is there
					new File(projectBuildPath() + "/META-INF/").mkdirs();
					FileWriter writer = new FileWriter(projectBuildPath() + "/META-INF/prescan");
					for (String item : interesting) {
						writer.write(item + System.lineSeparator());
					}
					writer.flush();
				} else {
					getLog().info("No interesting web resources found?");
				}
			}
		} catch (Exception ex) {
			throw new MojoExecutionException(ex.getMessage(), ex);
		}
	}

	private URLClassLoader makeClassLoaderFromProjectDependencies() {
		List<URL> urls = new ArrayList<>();

		File outputDir = new File(project.getBuild().getOutputDirectory());

		if (outputDir.exists() && outputDir.isDirectory()) {
			try {
				urls.add(outputDir.toURI().toURL());

			} catch (MalformedURLException e) {
				getLog().error("Unable to get url for output folder: " + outputDir.getAbsolutePath());
			}
		}

		project.setArtifactFilter(artifact -> Artifact.SCOPE_COMPILE.equals(artifact.getScope()) || Artifact.SCOPE_RUNTIME.equals(artifact.getScope()));

		project.getArtifacts().forEach( artifact -> {
			try {
				urls.add(artifact.getFile().toURI().toURL());
			} catch (MalformedURLException e) {
				getLog().error("Unable to get url for dependency!", e);
			}
		});

		return new URLClassLoader(urls.toArray(new URL[0]));
	}

	private void add(List<String> found, String type, String path) {
		getLog().info(String.format("prescan-resource: %s -> `%s`", type, path));
		found.add(String.format("%s=%s", type, path));
	}

	// in the master jar it will be META-INF/resources and in sub-jars it will end with a /
	private static final List<String> resourceOptions = Arrays.asList("META-INF/resources/", "META-INF/resources");

	public List<String> scan(ClasspathScanner scanner, ClassLoader classLoader) throws Exception {
		List<String> found = new ArrayList<>();

		ResourceScanListener listener = new ResourceScanListener() {
			@Override
			public List<ScanResource> resource(List<ScanResource> scanResources) throws Exception {
				for (ScanResource scanResource : scanResources) {
					if (scanResource.resourceName.endsWith("WEB-INF/web.xml")) {
						add(found, "webxml", dereferenceResourcePath(scanResource));
					} else if ("META-INF/web-fragment.xml".equals(scanResource.resourceName)) {
						add(found, "fragment", dereferenceResourcePath(scanResource));
					} else if (resourceOptions.contains(scanResource.resourceName) && isDirectory(scanResource)) {
						String path = dereferenceResourcePath(scanResource);
						if (!path.endsWith("/")) {
							path = path + "/";
						}

						add(found, "resource", path);
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
	 * <p>
	 * When processing a jar file. The URL is going to be something like
	 * jar:file:/home/user/.m2/repository/org/bob/servlet/2.4.1.Final/servlet-2.4.1.Final.jar!/META-INF/web-fragment.xml
	 * however it needs to look something like
	 * jar:file:/lib/servlet-2.4.1.Final.jar!/META-INF/web-fragment.xml
	 */
	private String dereferenceResourcePath(ResourceScanListener.ScanResource resource) {
		String url = resource.getResolvedUrl().toString();
		if (url.contains("!")) {
			String[] bits = url.split("!");
			String prefix = bits[0].substring(0, bits[0].indexOf("/") + 1);
			String jarfile = bits[0].substring(bits[0].lastIndexOf("/"));
			bits[0] = prefix + jarpath + jarfile;
			url = String.join("!", bits);
		} else {
			url = "jar:" + url;
		}
		return url.replace(projectBuildPath(), "");

	}

	private String projectBuildPath() {
		String path = projectBuildDir.getPath();
		// are we in a test?
		if (!path.endsWith("classes")) {
			return path + "/classes";
		}
		return path;
	}

	private boolean isDirectory(ResourceScanListener.ScanResource scanResource) {
		return (scanResource.file != null && scanResource.file.isDirectory()) ||
			(scanResource.entry != null && scanResource.entry.isDirectory());
	}
}
