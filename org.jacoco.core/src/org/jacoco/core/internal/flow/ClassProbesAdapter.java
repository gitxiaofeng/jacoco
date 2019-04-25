/*******************************************************************************
 * Copyright (c) 2009, 2019 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *    
 *******************************************************************************/
package org.jacoco.core.internal.flow;

import org.jacoco.core.internal.instr.InstrSupport;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.AnalyzerAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link org.objectweb.asm.ClassVisitor} that calculates probes for every
 * method.
 */
public class ClassProbesAdapter extends ClassVisitor implements
		IProbeIdGenerator {

	private static final MethodProbesVisitor EMPTY_METHOD_PROBES_VISITOR = new MethodProbesVisitor() {
	};

	private final ClassProbesVisitor cv;

	private final boolean trackFrames;
	private final List<String> methodNames;
	private int counter = 0;

	private String name;

	/**
	 * Creates a new adapter that delegates to the given visitor.
	 * 
	 * @param cv
	 *            instance to delegate to
	 * @param trackFrames
	 *            if <code>true</code> stackmap frames are tracked and provided
	 */
	public ClassProbesAdapter(final ClassProbesVisitor cv,
			final boolean trackFrames, final List<String> methodNames) {
		super(InstrSupport.ASM_API_VERSION, cv);
		this.cv = cv;
		this.trackFrames = trackFrames;
		this.methodNames = methodNames;
	}

	@Override
	public void visit(final int version, final int access, final String name,
			final String signature, final String superName,
			final String[] interfaces) {
		this.name = name;
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public final MethodVisitor visitMethod(final int access, final String name,
			final String desc, final String signature, final String[] exceptions) {
		final MethodProbesVisitor methodProbes;
		final MethodProbesVisitor mv = cv.visitMethod(access, name, desc,
				signature, exceptions);
		System.out.println("name is: " + name);
		System.out.println("cv is: " + cv.toString());
		System.out.println("trackFrames is: " + trackFrames);
		String[] names = this.name.split("/");
		if (mv != null && (methodNames == null || methodNames.isEmpty() || methodNames.contains(names[names.length-1]))) {
			System.out.println("name is included : " + name);
			methodProbes = mv;
		} else {
			// We need to visit the method in any case, otherwise probe ids
			// are not reproducible
			System.out.println("name is not included : " + name);
			methodProbes = EMPTY_METHOD_PROBES_VISITOR;
		}
		return new MethodSanitizer(null, access, name, desc, signature,
				exceptions) {

			@Override
			public void visitEnd() {
				super.visitEnd();
				LabelFlowAnalyzer.markLabels(this);
				final MethodProbesAdapter probesAdapter = new MethodProbesAdapter(
						methodProbes, ClassProbesAdapter.this);
				if (trackFrames) {
					final AnalyzerAdapter analyzer = new AnalyzerAdapter(
							ClassProbesAdapter.this.name, access, name, desc,
							probesAdapter);
					probesAdapter.setAnalyzer(analyzer);
					methodProbes.accept(this, analyzer);
				} else {
					methodProbes.accept(this, probesAdapter);
				}
			}
		};
	}

	@Override
	public void visitEnd() {
		cv.visitTotalProbeCount(counter);
		super.visitEnd();
	}

	// === IProbeIdGenerator ===

	public int nextId() {
		return counter++;
	}

}
