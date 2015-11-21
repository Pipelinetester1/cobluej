#summary One-sentence summary of this page.

# BlueJ #

El proyecto [BlueJ](http://www.bluej.org) será utilizado como base para esta versión colaborativa del mismo. Esta sección será utilizada para documentar los aspectos importantes de esta aplicación, son el objetivo de que sean la base para extenderla y agregarle capacidades colaborativas.

# Sub Proyectos #

## Boot ##
Entre los subproyectos que maneja BlueJ, se encuentra **boot**, es un proyecto bastante pequeño, y su única utilidad es la de mostrar un _splash_ al inicio de una aplicación.

# BlueJ Extensions #
BlueJ expone un api que permite la creación de extensiones. se puede encontrar [ejemplos](http://www.bluej.org/doc/writingextensions.html) y la [documentación](http://www.bluej.org/doc/extensionsAPI/index.html) completa en la página oficial de BlueJ.

# Notas para el programador #
A pesar de ser un sub proyecto dentro del conjunto de proyectos que conforman BlueJ, su funcionamiento es algo inesperado, este proyecto es el encargado de iniciar la aplicación, pero lo hace mediante **Reflection**, el objetivo es evitar _referencias cíclicas_ entre **BlueJ** y **Boot**, ya que boot es una libreria requerida por el proyecto princical (BlueJ).
Personalmente creo que seria mejor evitar este proceso, Tomando en cuenta que se iniciará un nuevo proyecto que contenga las nuevas caracteríscas (cobluej) podríamos optar por usar _boot_ coo una libreria e instanciarla desde este nuevo proyecto.

Existe demasida dependencia encuanto a reflection, también se hace uso de librerias que al parecer son cargadas en tiempo de ejecución. Las opciones que veo son:
  * Eliminar estas depencias modificando y refactorizando el codigo de bluej y boot, desde mi punto de vista _boot_ deberia ser una libreria más generica, y no tener atributos propios de BlueJ o peor de GreenFoot (Version, classpath, etc).
  * No cambiar nada en el código, cambiar los classpath de manera que se comporte como se espera originalmente, personalmente creo que esta opción es la mas rápida y fácil, pero no me agrada :(.

Revisando más a fondo el resultado de la compilación del código original de bluej, pude notar que se crean varias librerias como resultado (bluej.jar, bluejcore.jar, bluejext.jar y bluejeditor.jar). Así que me inclino más por la opción de reutilizar estas librerias, y extenderlas usando un UI propio (posiblemente heredando classes de UI del bluej).

Actualizaci&oacute;n:
Al parecer el use del API que expone BlueJ para crear extensiones, es bastante completo y es una opción viable para continuar con este proyecto. Lamentablemente al intentar usar o extender clases del propio BlueJ, me topé varias veces con los mismos problemas de dependencias.

Eliminar las dependencias sería la solución. Salvo ya hayas salvado esos obstáculos <br />
- Utilizar la api que proporciona BlueJ para crear extensiones soluciona el problema de dependencia, pero el problema es que nos vemos limitados por dicho API. pero aun asi creo q esta es la mejor opcion.