package cd.connect.prescan

import com.bluetrainsoftware.classpathscanner.ClasspathScanner
import spock.lang.Specification

class PrescanGeneratorMojoSpec extends Specification{

	void "We scan for our artifacts"(){
		given: "we have a scanner and a classloader"
			ClasspathScanner scanner = ClasspathScanner.getInstance()
			ClassLoader loader = getClass().getClassLoader()
			PrescanGeneratorMojo mojo = new PrescanGeneratorMojo()
			mojo.projectBuildDir = new File( getClass().getResource('/' ).getFile() )

		when: "we perform a scan"
			List<String> found = mojo.scan( scanner, loader )

		then: "we expect to find stuff"
			found.find { 'webxml=file:/WEB-INF/web.xml' == it }
			found.find { 'resource=file:/META-INF/resources/' == it }
			!found.find { 'resource=file:/META-INF/resources/empty.txt' == it }
			found.find { 'fragment=file:/META-INF/web-fragment.xml' == it }
			found.find { 'webxml=file:/META-INF/resources/WEB-INF/web.xml' == it }
	}

	void "We scan our jars for artifacts"(){
		given: "we have a scanner and a classloader"
			ClasspathScanner scanner = ClasspathScanner.getInstance()
			ClassLoader loader = new URLClassLoader( asURLArray( [ '/jars/binks.jar', '/jars/fragment.jar' ] ) )
			PrescanGeneratorMojo mojo = new PrescanGeneratorMojo()
			mojo.projectBuildDir = new File( getClass().getResource('/' ).getFile() )
			mojo.jarpath = "lib"

		when: "we perform a scan"
			List<String> found = mojo.scan( scanner, loader )

		then: "we expect to find stuff"
			found.size() == 3
			found.find { 'webxml=jar:file:/lib/binks.jar!/WEB-INF/web.xml' == it }
			found.find { 'fragment=jar:file:/lib/fragment.jar!/META-INF/web-fragment.xml' == it }
			found.find { 'resource=jar:file:/lib/fragment.jar!/META-INF/resources/' == it }
	}

	private static URL[] asURLArray( List<String> strings ) {
		return strings.collect { getClass().getResource( it ).toURI().toURL() }.toArray() as URL[]
	}

}
