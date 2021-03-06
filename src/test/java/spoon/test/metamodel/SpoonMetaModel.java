/**
 * Copyright (C) 2006-2017 INRIA and contributors
 * Spoon - http://spoon.gforge.inria.fr/
 *
 * This software is governed by the CeCILL-C License under French law and
 * abiding by the rules of distribution of free software. You can use, modify
 * and/or redistribute the software under the terms of the CeCILL-C license as
 * circulated by CEA, CNRS and INRIA at http://www.cecill.info.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the CeCILL-C License for more details.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-C license and that you accept its terms.
 */
package spoon.test.metamodel;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import spoon.Launcher;
import spoon.SpoonException;
import spoon.reflect.annotations.PropertyGetter;
import spoon.reflect.annotations.PropertySetter;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.AllTypeMembersFunction;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.FileSystemFolder;
import spoon.support.visitor.ClassTypingContext;

/**
 * Represents a Spoon meta model of the AST nodes.
 */
public class SpoonMetaModel {
	public static final String CLASS_SUFFIX = "Impl";
	/**
	 * qualified names of packages which contains interfaces of spoon model
	 */
	public static final Set<String> MODEL_IFACE_PACKAGES = new HashSet<>(Arrays.asList(
			"spoon.reflect.code",
			"spoon.reflect.declaration",
			"spoon.reflect.reference"));

	private final Factory factory;
	
	/**
	 * {@link MMType}s by name
	 */
	private final Map<String, MMType> name2mmType = new HashMap<>();

	/**
	 * Parses spoon sources and creates factory with spoon model.
	 *
	 * @param spoonJavaSourcesDirectory the root directory of java sources of spoon model.
	 * 	The directory must contain "spoon" directory.
	 */
	public SpoonMetaModel(File spoonJavaSourcesDirectory) {
		this(createFactory(spoonJavaSourcesDirectory));
	}

	/**
	 * @param factory already loaded factory with all Spoon model types
	 */
	public SpoonMetaModel(Factory factory) {
		this.factory =  factory;
		
		for (String apiPackage : MODEL_IFACE_PACKAGES) {
			if (factory.Package().get(apiPackage) == null) {
				throw new SpoonException("Spoon Factory model is missing API package " + apiPackage);
			}
			String implPackage = replaceApiToImplPackage(apiPackage);
			if (factory.Package().get(implPackage) == null) {
				throw new SpoonException("Spoon Factory model is missing implementation package " + implPackage);
			}
		}
		
		//search for all interfaces of spoon model and create MMTypes for them
		factory.getModel().filterChildren(new TypeFilter<>(CtInterface.class))
			.forEach((CtInterface<?> iface) -> {
				if (MODEL_IFACE_PACKAGES.contains(iface.getPackage().getQualifiedName())) {
					getOrCreateMMType(iface);
				}
			});
	}

	/**
	 * @return all {@link MMType}s of spoon meta model
	 */
	public Collection<MMType> getMMTypes() {
		return Collections.unmodifiableCollection(name2mmType.values());
	}
	
	/**
	 * @param type a spoon model type
	 * @return name of {@link MMType}, which represents Spoon model {@link CtType}
	 */
	public static String getMMTypeIntefaceName(CtType<?> type) {
		String name = type.getSimpleName();
		if (name.endsWith(CLASS_SUFFIX)) {
			name = name.substring(0, name.length() - CLASS_SUFFIX.length());
		}
		return name;
	}
	

	/**
	 * @param iface the interface of spoon model element
	 * @return {@link CtClass} of Spoon model which implements the spoon model interface. null if there is no implementation.
	 */
	public static CtClass<?> getImplementationOfInterface(CtInterface<?> iface) {
		String impl = replaceApiToImplPackage(iface.getQualifiedName()) + CLASS_SUFFIX;
		return (CtClass<?>) iface.getFactory().Type().get(impl);
	}

	private static final String modelApiPackage = "spoon.reflect";
	private static final String modelApiImplPackage = "spoon.support.reflect";
	
	private static String replaceApiToImplPackage(String modelInterfaceQName) {
		if (modelInterfaceQName.startsWith(modelApiPackage) == false) {
			throw new SpoonException("The qualified name doesn't belong to Spoon model API package: " + modelApiPackage);
		}
		return modelApiImplPackage + modelInterfaceQName.substring(modelApiPackage.length());
	}
	/**
	 * @param impl the implementation of spoon model element
	 * @return {@link CtInterface} of Spoon model which represents API of the spoon model class. null if there is no implementation.
	 */
	public static CtInterface<?> getInterfaceOfImplementation(CtClass<?> impl) {
		String iface = impl.getQualifiedName();
		if (iface.endsWith(CLASS_SUFFIX) == false || iface.startsWith("spoon.support.reflect.") == false) {
			throw new SpoonException("Unexpected spoon model implementation class: " + impl.getQualifiedName());
		}
		iface = iface.substring(0, iface.length() - CLASS_SUFFIX.length());
		iface = iface.replace("spoon.support.reflect", "spoon.reflect");
		return (CtInterface<?>) impl.getFactory().Type().get(iface);
	}

	private static Factory createFactory(File spoonJavaSourcesDirectory) {
		final Launcher launcher = new Launcher();
		launcher.getEnvironment().setNoClasspath(true);
		launcher.getEnvironment().setCommentEnabled(true);
//		// Spoon model interfaces
		Arrays.asList("spoon/reflect/code",
				"spoon/reflect/declaration",
				"spoon/reflect/reference",
				"spoon/support/reflect/declaration",
				"spoon/support/reflect/code",
				"spoon/support/reflect/reference").forEach(path -> {
			launcher.addInputResource(new FileSystemFolder(new File(spoonJavaSourcesDirectory, path)));
		});
		launcher.buildModel();
		return launcher.getFactory();
	}

	/**
	 * @param type can be class or interface of Spoon model element
	 * @return existing or creates and initializes new {@link MMType} which represents `type`, which 
	 */
	private MMType getOrCreateMMType(CtType<?> type) {
		String mmTypeName = getMMTypeIntefaceName(type);
		MMType mmType = getOrCreate(name2mmType, mmTypeName, () -> new MMType());
		if (mmType.name == null) {
			mmType.name = mmTypeName;
			initializeMMType(type, mmType);
		}
		return mmType;
	}
	
	/**
	 * is called once for each MMType, to initialize it.
	 * @param type a class or inteface of the spoon model element
	 * @param mmType to be initialize MMType
	 */
	private void initializeMMType(CtType<?> type, MMType mmType) {
		//it is not initialized yet. Do it now
		if (type instanceof CtInterface<?>) {
			CtInterface<?> iface = (CtInterface<?>) type;
			mmType.setModelClass(getImplementationOfInterface(iface));
			mmType.setModelInterface(iface);
		} else if (type instanceof CtClass<?>) {
			CtClass<?> clazz = (CtClass<?>) type;
			mmType.setModelClass(clazz);
			mmType.setModelInterface(getInterfaceOfImplementation(clazz));
		} else {
			throw new SpoonException("Unexpected spoon model type: " + type.getQualifiedName());
		}

		//add fields of class
		if (mmType.getModelClass() != null) {
			addFieldsOfType(mmType, mmType.getModelClass());
		}
		//add fields of interface
		if (mmType.getModelInterface() != null) {
			//add fields of interface too. They are not added by above call of addFieldsOfType, because the MMType already exists in name2mmType
			addFieldsOfType(mmType, mmType.getModelInterface());
		}
		//initialize all fields
		mmType.getRole2field().forEach((role, mmField) -> {
			//if there are more methods for the same field then choose the one which best matches the field type
			mmField.sortByBestMatch();
			//finally initialize value type of this field
			mmField.setValueType(mmField.detectValueType());
		});
	}

	/**
	 * adds all {@link MMField}s of `ctType`
	 * @param mmType the owner of to be created fields
	 * @param ctType to be scanned {@link CtType}
	 */
	private void addFieldsOfType(MMType mmType, CtType<?> ctType) {
		ctType.getTypeMembers().forEach(typeMember -> {
			if (typeMember instanceof CtMethod<?>) {
				CtMethod<?> method = (CtMethod<?>) typeMember;
				CtRole role = getRoleOfMethod(method);
				if (role != null) {
					MMField field = mmType.getOrCreateMMField(role);
					field.addMethod(method);
				} else {
					mmType.otherMethods.add(method);
				}
			}
		});
		addFieldsOfSuperType(mmType, ctType.getSuperclass());
		ctType.getSuperInterfaces().forEach(superIfaceRef -> addFieldsOfSuperType(mmType, superIfaceRef));
	}

	private static Set<String> EXPECTED_TYPES_NOT_IN_CLASSPATH = new HashSet<>(Arrays.asList(
			"java.lang.Cloneable",
			"spoon.processing.FactoryAccessor",
			"spoon.reflect.visitor.CtVisitable",
			"spoon.reflect.visitor.chain.CtQueryable",
			"spoon.template.TemplateParameter",
			"java.lang.Iterable",
			"java.io.Serializable"));


	/**
	 * add all fields of `superTypeRef` into `mmType`
	 * @param mmType sub type
	 * @param superTypeRef super type
	 */
	private void addFieldsOfSuperType(MMType mmType, CtTypeReference<?> superTypeRef) {
		if (superTypeRef == null) {
			return;
		}
		CtType<?> superType = superTypeRef.getDeclaration();
		if (superType == null) {
			if (EXPECTED_TYPES_NOT_IN_CLASSPATH.contains(superTypeRef.getQualifiedName()) == false) {
				throw new SpoonException("Cannot create spoon meta model. The class " + superTypeRef.getQualifiedName() + " is missing class path");
			}
			return;
		}
		//call getOrCreateMMType recursively for super types
		MMType superMMType = getOrCreateMMType(superType);
		if (superMMType != mmType) {
			mmType.addSuperType(superMMType);
		}
	}

	static <K, V> V getOrCreate(Map<K, V> map, K key, Supplier<V> valueCreator) {
		V value = map.get(key);
		if (value == null) {
			value = valueCreator.get();
			map.put(key, value);
		}
		return value;
	}
	static <T> boolean addUniqueObject(Collection<T> col, T o) {
		if (containsObject(col, o)) {
			return false;
		}
		col.add(o);
		return true;
	}
	static boolean containsObject(Iterable<? extends Object> iter, Object o) {
		for (Object object : iter) {
			if (object == o) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @param method to be checked method
	 * @return {@link CtRole} of spoon model method. Looking into all super class/interface implementations of this method
	 */
	public static CtRole getRoleOfMethod(CtMethod<?> method) {
		Factory f = method.getFactory();
		CtAnnotation<PropertyGetter> getter = getInheritedAnnotation(method, f.createCtTypeReference(PropertyGetter.class));
		if (getter != null) {
			return getter.getActualAnnotation().role();
		}
		CtAnnotation<PropertySetter> setter = getInheritedAnnotation(method, f.createCtTypeReference(PropertySetter.class));
		if (setter != null) {
			return setter.getActualAnnotation().role();
		}
		return null;
	}

	/**
	 * @param method a start method
	 * @param annotationType a searched annotation type
	 * @return annotation from the first method in superClass and superInterface hierarchy for the method with required annotationType
	 */
	private static <A extends Annotation> CtAnnotation<A> getInheritedAnnotation(CtMethod<?> method, CtTypeReference<A> annotationType) {
		CtAnnotation<A> annotation = method.getAnnotation(annotationType);
		if (annotation == null) {
			CtType<?> declType = method.getDeclaringType();
			final ClassTypingContext ctc = new ClassTypingContext(declType);
			annotation = declType.map(new AllTypeMembersFunction(CtMethod.class)).map((CtMethod<?> currentMethod) -> {
				if (method == currentMethod) {
					return null;
				}
				if (ctc.isSameSignature(method, currentMethod)) {
					CtAnnotation<A> annotation2 = currentMethod.getAnnotation(annotationType);
					if (annotation2 != null) {
						return annotation2;
					}
				}
				return null;
			}).first();
		}
		return annotation;
	}

	public Factory getFactory() {
		return factory;
	}
}
