package de.wr.annotationprocessor.processor

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.ThisExpr
import com.github.javaparser.ast.expr.TypeExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.ReturnStmt
import com.github.javaparser.ast.type.ClassOrInterfaceType
import de.wr.libsimpledefaultmethods.*
import io.reactivex.Observable
import io.reactivex.rxkotlin.toObservable
import java.io.BufferedWriter
import java.io.IOException
import java.util.*
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.SourceVersion.latestSupported
import javax.lang.model.element.*
import javax.lang.model.type.TypeKind
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic
import kotlin.collections.HashSet
import com.github.javaparser.ast.Modifier as AstModifier
import com.github.javaparser.ast.type.Type as AstType

class SimpleDefaultMethodsInterfaceProcessor : AbstractProcessor() {

    private val methodsForClass = Hashtable<TypeElement, List<ExecutableElement>>()

    private lateinit var objectType: String
    private lateinit var typeUtils: Types
    private lateinit var elementUtils: Elements
    private lateinit var filer: Filer
    private lateinit var messager: Messager

    override fun getSupportedSourceVersion(): SourceVersion {
        return latestSupported()
    }

    override fun getSupportedAnnotationTypes() = supportedAnnotations

    @Synchronized override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        typeUtils = processingEnv.typeUtils
        elementUtils = processingEnv.elementUtils
        filer = processingEnv.filer
        messager = processingEnv.messager
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {

        val methodPair = supportedAnnotationsClasses.toObservable()
                .flatMap {
                    roundEnv.getElementsAnnotatedWith(it).toObservable()
                }.flatMap { el ->
            when {
                el.kind == ElementKind.CLASS -> Observable.just(el)
                        .flatMap { it.enclosedElements.toObservable() }
                        .filter { it is ExecutableElement }
                        .map { Pair(el, it) }
                el.kind == ElementKind.PARAMETER -> when {
                    el.enclosingElement != null -> Observable.just(Pair(el, el.enclosingElement))
                    else -> Observable.empty()
                }.filter { it.second is ExecutableElement }
                else -> Observable.just(Pair(el, el))
            }
        }.blockingIterable()

        val clazzes = methodPair.groupBy { it.second }
                .entries
                .groupBy { it.key.enclosingElement }

        generateMethodsForClazz(clazzes)

        return true
    }

    private fun generateMethodsForClazz(clazzes: Map<Element, List<Map.Entry<Element, List<Pair<Element, Element>>>>>) {

        clazzes.forEach {

            val clazzElement = it.key as TypeElement

            info(clazzElement, "Class containing defaults found %s", clazzElement )

            try {
                val fileName = clazzElement.simpleName.toString() + "Defaults"
                val source = processingEnv.filer.createSourceFile(fileName)

                val writer = BufferedWriter(source.openWriter())

                val cu = CompilationUnit();
                // set the package
                cu.setPackageDeclaration(getPackageName(clazzElement));

                val interf = cu.addInterface(fileName, AstModifier.PUBLIC)

                it.value.forEach {

                    val method = it.key as ExecutableElement

                    info(method, "Class %s contains default method %s", clazzElement, method.toString() )

                    if (method.modifiers.contains(Modifier.PRIVATE)) {
                        error(method, "Class %s contains private default method %s which is not supported !", clazzElement, method.toString() )
                    } else {

                        val methodName = method.simpleName.toString()

                        //create method inside interface
                        if (!method.modifiers.contains(Modifier.STATIC) && method.modifiers.contains(Modifier.PUBLIC)) {
                            val realMethod = interf.addMethod(methodName)
                                    .setType(method.returnType.toString())
                                    .removeBody()

                            method.parameters.forEach {
                                realMethod.addParameter(it.asType().toString(), it.simpleName.toString())
                            }
                        }

                        val defValueMap: List<Pair<VariableElement, String>>

                        if (it.value.any { it.first == method }) { // All parameters should get default values
                            defValueMap = method.parameters.map { Pair(it, getDefaultValue(it)) }
                        } else {
                            // check starting from the end of the param list
                            var lastPair: Pair<VariableElement, String>? = null
                            val reversed = method.parameters.reversed()
                            val defValueMapTemp = mutableListOf<Pair<VariableElement, String>>()
                            for (param in reversed) {
                                val pair = Pair(param, getDefaultValue(param, false))
                                if (pair.second.isNotEmpty()) {
                                    if (lastPair?.second?.isEmpty() == true) {
                                        error(param, "Default parameter cannot be followed by non default one: %s", pair.first)
                                    }
                                }
                                lastPair = pair
                                defValueMapTemp.add(pair)
                            }
                            defValueMap = defValueMapTemp.reversed()
                        }

                        defValueMap
                                .dropWhile { it.second.isEmpty() } // ensures non default elements at the beginning are included
                                .forEach { currentParam ->
                                    // create default methods
                                    val defMethodExp = MethodCallExpr(
                                            if (method.modifiers.contains(Modifier.STATIC)) {
                                                TypeExpr(ClassOrInterfaceType(null, clazzElement.simpleName.toString()))
                                            } else if (!method.modifiers.contains(Modifier.PUBLIC)) {
                                                NameExpr("thizz")
                                            } else {
                                                ThisExpr()
                                            }
                                    , methodName)

                                    val passedParams = defValueMap.takeWhile { it != currentParam }

                                    if (DEFAULT) {
                                        info(method, "Passed parameters size %s", passedParams.size)
                                    }

                                    defValueMap.forEach {
                                        val (name, value) = it
                                        if (DEFAULT) {
                                            info(method, "Try to set arg %s = %s", name, value)
                                        }
                                        defMethodExp.addArgument(if (value.isNotEmpty() && !passedParams.contains(it)) value else name.simpleName.toString())
                                    }

                                    val block = when {
                                        method.returnType.kind == TypeKind.VOID -> BlockStmt().addStatement(defMethodExp)
                                        else -> BlockStmt().addStatement(ReturnStmt().setExpression(defMethodExp))
                                    }

                                    val defMethod = interf.addMethod(methodName,
                                            if (method.modifiers.contains(Modifier.STATIC) || !method.modifiers.contains(Modifier.PUBLIC)) {
                                                AstModifier.STATIC
                                            } else {
                                                AstModifier.DEFAULT
                                            })
                                    if (!method.modifiers.contains(Modifier.STATIC) && !method.modifiers.contains(Modifier.PUBLIC)) {
                                        defMethod.addParameter(clazzElement.simpleName.toString(), "thizz")
                                    }
                                    defValueMap
                                            .filter { it.second.isEmpty() || passedParams.contains(it) }
                                            .forEach {
                                                if (DEFAULT) {
                                                    info(method, "Added no default param %s %s", it.first.asType().toString(), it.first.simpleName)
                                                }
                                                defMethod.addParameter(it.first.asType().toString(), it.first.simpleName.toString())
                                            }
                                    defMethod.setBody(block)
                                            .setType(method.returnType.toString())
                                }
                    }
                }

                writer.run {
                    write(cu.toString())
                    flush()
                    close()
                }

                info(clazzElement, "Interface default generated: %s %n", fileName)
            } catch (e: IOException) {
                System.err.println(objectType + " :" + e + e.message)
                error(clazzElement, "Error: %s %n", e)
            }
        }
    }

    private fun getDefaultValue(type: VariableElement, allowNonParams: Boolean = true): String {
        val returnValue = when(type.asType().kind) {
            TypeKind.INT -> type.getAnnotation(DefaultInt::class.java)?.value?.toString() ?:
                    if (!allowNonParams) "" else "0"
            TypeKind.BOOLEAN -> type.getAnnotation(DefaultBool::class.java)?.value?.toString() ?:
                    if (!allowNonParams) "" else "false"
            TypeKind.BYTE -> "(byte)" + (type.getAnnotation(DefaultByte::class.java)?.value?.toString() ?:
                    if (!allowNonParams) "" else "0")
            TypeKind.SHORT -> "(short)" + (type.getAnnotation(DefaultShort::class.java)?.value?.toString() ?:
                    if (!allowNonParams) "" else "0")
            TypeKind.LONG -> (type.getAnnotation(DefaultLong::class.java)?.value?.toString() ?:
                    if (!allowNonParams) "" else "0") + "l"
            TypeKind.CHAR -> (type.getAnnotation(DefaultChar::class.java)?.value?.toString()?.let { "'$it'" } ?:
                    if (!allowNonParams) "" else "'0'")
            TypeKind.FLOAT -> (type.getAnnotation(DefaultFloat::class.java)?.value?.toString() ?:
                    if (!allowNonParams) "" else "0") + "f"
            TypeKind.DOUBLE -> (type.getAnnotation(DefaultDouble::class.java)?.value?.toString() ?:
                    if (!allowNonParams) "" else "0") + "d"
            TypeKind.ARRAY -> if (type.getAnnotation(Default::class.java) == null && !allowNonParams) "" else "new " + convertIfGeneric(type.asType().toString()) + "[0]"
            else -> when (type.asType().toString()) {
                "java.lang.String" -> type.getAnnotation(DefaultString::class.java)?.value?.let { "\"$it\"" } ?:
                        if (!allowNonParams) "" else "\"\""
                else -> if (type.getAnnotation(Default::class.java) == null && !allowNonParams) "" else "null"
            }
        }
        if (DEFAULT) { info(type, "Default value for %s = %s", type, returnValue) }
        return returnValue
    }

    private fun convertIfGeneric(type: String): String {
        val regex = Regex(".*(<.*>\\[])")
        val result = regex.find(type)
        val matchGroup = result?.groups?.get(1)
        return matchGroup?.let { type.replaceFirst(it.value, "") } ?: type
    }

    private fun getPackageName(typeElement: TypeElement) =
            typeElement.qualifiedName.substring(0, typeElement.qualifiedName.length - typeElement.simpleName.length - 1)

    private fun error(e: Element, msg: String, vararg args: Any) {
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, *args),
                e)
    }

    private fun info(e: Element, msg: String, vararg args: Any) {
        messager.printMessage(
                Diagnostic.Kind.WARNING,
                String.format(msg, *args),
                e)
    }


    companion object {
        private var supportedAnnotations = HashSet<String>()
        private var supportedAnnotationsClasses = mutableListOf<Class<out Annotation>>()

        init {
            supportedAnnotationsClasses.apply {
                add(DefaultInt::class.java)
                add(DefaultLong::class.java)
                add(DefaultShort::class.java)
                add(DefaultByte::class.java)
                add(DefaultChar::class.java)
                add(DefaultBool::class.java)
                add(DefaultFloat::class.java)
                add(DefaultDouble::class.java)
                add(DefaultString::class.java)
                add(Default::class.java)
            }.forEach {
                supportedAnnotations.add(it.canonicalName)
            }
        }

        val DEFAULT = false
    }
}